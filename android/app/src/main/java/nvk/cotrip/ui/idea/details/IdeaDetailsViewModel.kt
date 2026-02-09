package nvk.cotrip.ui.idea.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nvk.cotrip.BuildConfig
import nvk.cotrip.R
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.ws.CommentCreatedPayload
import nvk.cotrip.data.network.ws.CommentWsEvent
import nvk.cotrip.data.network.ws.CommentsWebSocket
import nvk.cotrip.ui.idea.common.IdeaDayOptionUi
import nvk.cotrip.ui.idea.common.IdeaDayPickerState
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class IdeaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val api: CoTripApi,
    private val sessionStore: SessionStore,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val json: Json,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.IdeaDetails.ARG_TRIP_ID])
    private val ideaId: String =
        checkNotNull(savedStateHandle[Destination.IdeaDetails.ARG_IDEA_ID])

    private val commentsSocket = CommentsWebSocket(okHttpClient, json)
    private var dayOptions: List<IdeaDayOptionUi> = emptyList()
    private var membersById: Map<String, MemberDto> = emptyMap()
    private var meId: String? = null
    private var currencySymbol: String = "€"

    private val _state = MutableStateFlow(
        IdeaDetailsState(
            tripId = tripId,
            ideaId = ideaId,
            title = "",
            city = "",
            cost = "",
            website = "",
            notes = "",
            addedDay = null,
            selectedTab = IdeaDetailsTab.Details,
            commentsCount = 0,
            discussion = emptyList(),
            commentInput = "",
            dayPicker = null
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<IdeaDetailsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeSocket()
        connectSocket()
        loadDetails()
    }

    override fun onCleared() {
        commentsSocket.disconnect()
        super.onCleared()
    }

    fun onEvent(event: IdeaDetailsEvent) {
        when (event) {
            IdeaDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            IdeaDetailsEvent.OnEditClick -> appNavigator.navigate(
                Destination.EditIdea(tripId, ideaId)
            )

            IdeaDetailsEvent.OnAddToItineraryClick -> openDayPicker()
            IdeaDetailsEvent.OnDeleteClick -> deleteIdea()
            IdeaDetailsEvent.OnDismissDayPicker -> dismissDayPicker()
            is IdeaDetailsEvent.OnDaySelected -> selectDay(event.day)
            is IdeaDetailsEvent.OnTabSelected -> _state.update { it.copy(selectedTab = event.tab) }
            is IdeaDetailsEvent.OnCommentChange -> _state.update { it.copy(commentInput = event.value) }
            IdeaDetailsEvent.OnSendComment -> sendComment()
        }
    }

    private fun loadDetails() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val ideaDeferred = async { api.getIdea(ideaId) }
                    val tripDeferred = async { api.getTrip(tripId) }
                    val itineraryDeferred = async { api.getItinerary(tripId) }
                    val membersDeferred = async { api.listMembers(tripId) }
                    val meDeferred = async { api.getMe() }
                    val commentsDeferred = async { api.listComments(ideaId) }

                    val idea = ideaDeferred.await()
                    val trip = tripDeferred.await()
                    val itinerary = itineraryDeferred.await().items
                    val members = membersDeferred.await().items
                    val me = meDeferred.await()
                    val comments = commentsDeferred.await().items

                    currencySymbol = currencySymbolFor(trip.currencyCode)
                    membersById = members.associateBy { it.userId }
                    meId = me.id
                    dayOptions = itinerary.filter { !it.isOutOfRange }.map { it.toDayOption() }

                    IdeaDetailsPayload(
                        idea = idea,
                        comments = comments
                    )
                }
            }.onSuccess { payload ->
                val idea = payload.idea
                val discussion = payload.comments
                    .sortedBy { parseTimestamp(it.createdAt)?.toEpochMilli() ?: 0L }
                    .map { it.toDiscussion(meId, membersById) }
                _state.update { current ->
                    current.copy(
                        title = idea.title,
                        city = idea.city.orEmpty(),
                        cost = idea.costAmount?.let { formatCost(it, currencySymbol) }.orEmpty(),
                        website = idea.website.orEmpty(),
                        notes = idea.notes.orEmpty(),
                        commentsCount = discussion.size,
                        discussion = discussion
                    )
                }
            }.onFailure {
                emit(IdeaDetailsEffect.ShowToastRes(R.string.common_error_message))
            }
        }
    }

    private fun connectSocket() {
        val token = sessionStore.getAccessToken().orEmpty()
        if (token.isBlank()) return
        commentsSocket.connect(BuildConfig.API_BASE_URL, tripId, token)
    }

    private fun observeSocket() {
        viewModelScope.launch {
            commentsSocket.events.collect { event ->
                when (event) {
                    is CommentWsEvent.CommentCreated -> handleCommentCreated(event.payload)
                    is CommentWsEvent.CommentDeleted -> handleCommentDeleted(event.payload.id)
                    is CommentWsEvent.Error -> Unit
                }
            }
        }
    }

    private fun handleCommentCreated(payload: CommentCreatedPayload) {
        if (payload.ideaId != ideaId) return
        _state.update { current ->
            val exists = current.discussion.any { item ->
                item is IdeaDiscussionItemUi.Message && item.id == payload.id
            }
            if (exists) return@update current
            val message = payload.toDiscussion(meId, membersById)
            current.copy(
                discussion = current.discussion + message,
                commentsCount = current.commentsCount + 1
            )
        }
    }

    private fun handleCommentDeleted(commentId: String) {
        _state.update { current ->
            val updated = current.discussion.filterNot {
                it is IdeaDiscussionItemUi.Message && it.id == commentId
            }
            if (updated.size == current.discussion.size) return@update current
            current.copy(
                discussion = updated,
                commentsCount = (current.commentsCount - 1).coerceAtLeast(0)
            )
        }
    }

    private fun openDayPicker() {
        if (_state.value.addedDay != null) return
        if (dayOptions.isEmpty()) {
            emit(IdeaDetailsEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        _state.update {
            it.copy(
                dayPicker = IdeaDayPickerState(
                    ideaId = ideaId,
                    days = dayOptions
                )
            )
        }
    }

    private fun dismissDayPicker() {
        _state.update { it.copy(dayPicker = null) }
    }

    private fun selectDay(day: IdeaDayOptionUi) {
        _state.update { it.copy(dayPicker = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.convertIdeaToActivity(ideaId, ConvertIdeaRequest(dayId = day.id))
                }
            }.onSuccess {
                _state.update { it.copy(addedDay = day.dayNumber) }
                emit(IdeaDetailsEffect.ShowToastRes(R.string.idea_details_added_toast))
            }.onFailure {
                emit(IdeaDetailsEffect.ShowToastRes(R.string.common_error_message))
            }
        }
    }

    private fun sendComment() {
        val input = _state.value.commentInput.trim()
        if (input.isBlank()) return
        val token = sessionStore.getAccessToken()
        if (token.isNullOrBlank()) {
            emit(IdeaDetailsEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        commentsSocket.sendCreate(ideaId, input)
        _state.update { it.copy(commentInput = "") }
    }

    private fun deleteIdea() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.deleteIdea(ideaId) }
            }.onSuccess {
                emit(IdeaDetailsEffect.ShowToastRes(R.string.idea_details_deleted_toast))
                appNavigator.popBackStack()
            }.onFailure {
                emit(IdeaDetailsEffect.ShowToastRes(R.string.common_error_message))
            }
        }
    }

    private fun emit(effect: IdeaDetailsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class IdeaDetailsPayload(
        val idea: IdeaDto,
        val comments: List<CommentDto>,
    )
}

private fun CommentDto.toDiscussion(
    meId: String?,
    membersById: Map<String, MemberDto>,
): IdeaDiscussionItemUi.Message {
    val member = membersById[authorId]
    val name = member?.name ?: "Unknown"
    val initials = member?.initials ?: initialsFromName(name)
    return IdeaDiscussionItemUi.Message(
        id = id,
        author = name,
        initials = initials,
        text = body,
        time = formatTimestamp(createdAt),
        isMe = authorId == meId
    )
}

private fun CommentCreatedPayload.toDiscussion(
    meId: String?,
    membersById: Map<String, MemberDto>,
): IdeaDiscussionItemUi.Message {
    val member = membersById[authorId]
    val name = member?.name ?: "Unknown"
    val initials = member?.initials ?: initialsFromName(name)
    return IdeaDiscussionItemUi.Message(
        id = id,
        author = name,
        initials = initials,
        text = body,
        time = formatTimestamp(createdAt),
        isMe = authorId == meId
    )
}

private fun ItineraryDayDto.toDayOption(): IdeaDayOptionUi {
    val date = LocalDate.parse(date)
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    return IdeaDayOptionUi(
        id = id,
        dayNumber = dayNumber,
        dateText = date.format(formatter),
        city = city.orEmpty(),
    )
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}

private fun formatCost(amount: Double, currencySymbol: String): String {
    val display = if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}

private fun parseTimestamp(raw: String): Instant? {
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant()
        }.getOrNull()
}

private fun formatTimestamp(raw: String): String {
    val instant = parseTimestamp(raw) ?: return raw
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

private fun initialsFromName(name: String): String {
    return name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
}
