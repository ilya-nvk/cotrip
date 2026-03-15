package nvk.cotrip.ui.idea.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nvk.cotrip.BuildConfig
import nvk.cotrip.R
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.ws.CommentCreatedPayload
import nvk.cotrip.data.network.ws.CommentDeletedPayload
import nvk.cotrip.data.network.ws.CommentWsEvent
import nvk.cotrip.data.network.ws.CommentsWebSocket
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.idea.common.IdeaDayOptionUi
import nvk.cotrip.ui.idea.common.IdeaDayPickerState
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import nvk.cotrip.util.AppLogger
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TripIdeasViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val ideaRepository: IdeaRepository,
    private val itineraryRepository: ItineraryRepository,
    private val sessionStore: SessionStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripIdeas.ARG_TRIP_ID])

    private var dayOptions: List<IdeaDayOptionUi> = emptyList()
    private var currencySymbol: String = "€"
    private val commentsSocket = CommentsWebSocket(okHttpClient, json)
    private var reconnectJob: Job? = null

    private val _state = MutableStateFlow<TripIdeasState>(TripIdeasState.Loading)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripIdeasEffect>()
    val effects = _effects.asSharedFlow()
    private val isRefreshing = MutableStateFlow(false)

    init {
        observeSocket()
        connectSocket()
        observeData()
        refreshIdeas(isUserRefresh = false)
    }

    override fun onCleared() {
        reconnectJob?.cancel()
        commentsSocket.disconnect()
        super.onCleared()
    }

    fun onEvent(event: TripIdeasEvent) {
        when (event) {
            TripIdeasEvent.OnBackClick -> appNavigator.popBackStack()
            TripIdeasEvent.OnAutoRefresh -> refreshIdeas(isUserRefresh = false)
            TripIdeasEvent.OnUserRefresh -> refreshIdeas(isUserRefresh = true)
            TripIdeasEvent.OnAddIdeaClick -> appNavigator.navigate(
                Destination.CreateIdea(tripId)
            )
            TripIdeasEvent.OnGetAiSuggestionsClick -> appNavigator.navigate(
                Destination.BuildRoute(tripId)
            )

            is TripIdeasEvent.OnIdeaClick -> appNavigator.navigate(
                Destination.IdeaDetails(tripId, event.ideaId)
            )

            is TripIdeasEvent.OnAddToItineraryClick -> openDayPicker(event.ideaId)
            TripIdeasEvent.OnDismissDayPicker -> dismissDayPicker()
            is TripIdeasEvent.OnDaySelected -> selectDay(event.day)
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                ideaRepository.observeIdeas(tripId),
                tripRepository.getTrip(tripId),
                itineraryRepository.observeItinerary(tripId),
                isRefreshing,
            ) { ideas, trip, itinerary, refreshing ->
                Quadruple(ideas, trip, itinerary, refreshing)
            }.collect { (ideas, trip, itinerary, refreshing) ->
                currencySymbol = currencySymbolFor(trip.currencyCode)
                dayOptions = itinerary
                    .filter { !it.isOutOfRange }
                    .map { it.toDayOption() }
                val addedDaysByIdea = collectAddedDays(itinerary)

                val updatedPicker =
                    (_state.value as? TripIdeasState.Content)?.dayPicker?.copy(days = dayOptions)
                _state.value = TripIdeasState.Content(
                    tripId = tripId,
                    ideas = ideas.map { idea ->
                        idea.toUi(currencySymbol, addedDaysByIdea[idea.id])
                    },
                    dayPicker = updatedPicker,
                    isRefreshing = refreshing,
                )
            }
        }
    }

    private fun refreshIdeas(isUserRefresh: Boolean) {
        viewModelScope.launch {
            if (isUserRefresh) {
                val current = _state.value as? TripIdeasState.Content
                if (current != null) {
                    _state.value = current.copy(isRefreshing = true)
                }
                isRefreshing.value = true
            }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.getTrip(tripId).first()
                    ideaRepository.refreshIdeas(tripId).getOrThrow()
                    itineraryRepository.refreshItinerary(tripId).getOrThrow()
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> emit(
                    TripIdeasEffect.ShowToastRes(
                        uiErrorMapper.messageRes(
                            result
                        )
                        )
                )
            }
            isRefreshing.value = false
        }
    }

    private fun openDayPicker(ideaId: String) {
        val current = _state.value as? TripIdeasState.Content ?: return
        if (dayOptions.isEmpty()) {
            emit(TripIdeasEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        _state.value =
            current.copy(dayPicker = IdeaDayPickerState(ideaId = ideaId, days = dayOptions))
    }

    private fun dismissDayPicker() {
        val current = _state.value as? TripIdeasState.Content ?: return
        _state.value = current.copy(dayPicker = null)
    }

    private fun selectDay(day: IdeaDayOptionUi) {
        val current = _state.value as? TripIdeasState.Content ?: return
        val ideaId = current.dayPicker?.ideaId ?: return
        _state.value = current.copy(dayPicker = null)
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    ideaRepository.convertIdeaToActivity(
                        ideaId,
                        ConvertIdeaRequest(dayId = day.id)
                    )
                }
            }) {
                is ApiResult.Success -> {
                    val latest = _state.value as? TripIdeasState.Content ?: return@launch
                    _state.value = latest.copy(
                        ideas = latest.ideas.map { idea ->
                            if (idea.id == ideaId) idea.copy(addedDay = day.dayNumber) else idea
                        }
                    )
                    withContext(Dispatchers.IO) {
                        itineraryRepository.refreshItinerary(tripId).getOrThrow()
                    }
                    emit(TripIdeasEffect.ShowToastRes(R.string.ideas_added_to_itinerary_toast))
                }

                is ApiResult.Failure -> emit(
                    TripIdeasEffect.ShowToastRes(
                        uiErrorMapper.messageRes(result)
                    )
                )
            }
        }
    }

    private fun connectSocket() {
        val token = sessionStore.getAccessToken().orEmpty()
        if (token.isBlank()) return
        reconnectJob?.cancel()
        commentsSocket.connect(BuildConfig.API_BASE_URL, tripId, token)
    }

    private fun observeSocket() {
        viewModelScope.launch {
            commentsSocket.events.collect { event ->
                when (event) {
                    is CommentWsEvent.CommentCreated -> handleCommentCreated(event.payload)
                    is CommentWsEvent.CommentDeleted -> handleCommentDeleted(event.payload)
                    is CommentWsEvent.CommentRejected -> Unit
                    is CommentWsEvent.Closed -> scheduleReconnect()
                    is CommentWsEvent.Error -> {
                        AppLogger.w(
                            TAG,
                            "trip ideas socket error, scheduling reconnect",
                            event.cause
                        )
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            delay(1_500L)
            connectSocket()
        }
    }

    private fun handleCommentCreated(payload: CommentCreatedPayload) {
        if (payload.type.equals("system", ignoreCase = true)) return
        val current = _state.value as? TripIdeasState.Content ?: return
        var changed = false
        val updated = current.ideas.map { idea ->
            if (idea.id == payload.ideaId) {
                changed = true
                idea.copy(commentsCount = idea.commentsCount + 1)
            } else {
                idea
            }
        }
        if (changed) {
            _state.value = current.copy(ideas = updated)
        }
    }

    private fun handleCommentDeleted(payload: CommentDeletedPayload) {
        val current = _state.value as? TripIdeasState.Content ?: return
        var changed = false
        val updated = current.ideas.map { idea ->
            if (idea.id == payload.ideaId) {
                changed = true
                idea.copy(commentsCount = (idea.commentsCount - 1).coerceAtLeast(0))
            } else {
                idea
            }
        }
        if (changed) {
            _state.value = current.copy(ideas = updated)
        }
    }

    private fun emit(effect: TripIdeasEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}

private fun collectAddedDays(itinerary: List<ItineraryDayDto>): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    itinerary
        .sortedBy { it.dayNumber }
        .forEach { day ->
            day.activities.forEach activityLoop@{ activity ->
                val ideaId = activity.sourceIdeaId ?: return@activityLoop
                result.putIfAbsent(ideaId, day.dayNumber)
            }
        }
    return result
}

private fun IdeaDto.toUi(currencySymbol: String, addedDay: Int?): IdeaListItemUi {
    val cost = costAmount?.let { formatCost(it, currencySymbol) }
    return IdeaListItemUi(
        id = id,
        title = title,
        city = city.orEmpty(),
        cost = cost,
        commentsCount = commentsCount,
        addedDay = addedDay
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

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private const val TAG = "TripIdeasVM"
