package nvk.cotrip.ui.idea.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nvk.cotrip.BuildConfig
import nvk.cotrip.R
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.ws.CommentDeletedPayload
import nvk.cotrip.data.network.ws.CommentWsEvent
import nvk.cotrip.data.network.ws.CommentCreatedPayload
import nvk.cotrip.data.network.ws.CommentsWebSocket
import nvk.cotrip.ui.idea.common.IdeaDayOptionUi
import nvk.cotrip.ui.idea.common.IdeaDayPickerState
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
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
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripIdeas.ARG_TRIP_ID])

    private var dayOptions: List<IdeaDayOptionUi> = emptyList()
    private var currencySymbol: String = "€"
    private val addedDays = mutableMapOf<String, Int>()
    private val commentsSocket = CommentsWebSocket(okHttpClient, json)

    private val _state = MutableStateFlow(
        TripIdeasState(
            tripId = tripId,
            ideas = emptyList(),
            dayPicker = null
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripIdeasEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeSocket()
        connectSocket()
        observeData()
        refreshIdeas()
    }

    override fun onCleared() {
        commentsSocket.disconnect()
        super.onCleared()
    }

    fun onEvent(event: TripIdeasEvent) {
        when (event) {
            TripIdeasEvent.OnBackClick -> appNavigator.popBackStack()
            TripIdeasEvent.OnRefresh -> refreshIdeas()
            TripIdeasEvent.OnAddIdeaClick -> appNavigator.navigate(
                Destination.CreateIdea(tripId)
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
                tripRepository.observeTrip(tripId),
                itineraryRepository.observeItinerary(tripId)
            ) { ideas, trip, itinerary ->
                Triple(ideas, trip, itinerary)
            }.collect { (ideas, trip, itinerary) ->
                if (trip != null) {
                    currencySymbol = currencySymbolFor(trip.currencyCode)
                }
                dayOptions = itinerary
                    .filter { !it.isOutOfRange }
                    .map { it.toDayOption() }

                _state.update { current ->
                    val updatedPicker = current.dayPicker?.copy(days = dayOptions)
                    current.copy(
                        ideas = ideas.map { idea ->
                            idea.toUi(currencySymbol, addedDays[idea.id])
                        },
                        dayPicker = updatedPicker
                    )
                }
            }
        }
    }

    private fun refreshIdeas() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    tripRepository.getTrip(tripId)
                    ideaRepository.refreshIdeas(tripId)
                    itineraryRepository.refreshItinerary(tripId)
                }
            }.onFailure {
                emit(TripIdeasEffect.ShowToastRes(R.string.common_error_message))
            }
        }
    }

    private fun openDayPicker(ideaId: String) {
        if (dayOptions.isEmpty()) {
            emit(TripIdeasEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        _state.update { current ->
            current.copy(dayPicker = IdeaDayPickerState(ideaId = ideaId, days = dayOptions))
        }
    }

    private fun dismissDayPicker() {
        _state.update { it.copy(dayPicker = null) }
    }

    private fun selectDay(day: IdeaDayOptionUi) {
        val ideaId = _state.value.dayPicker?.ideaId ?: return
        _state.update { it.copy(dayPicker = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ideaRepository.convertIdeaToActivity(
                        ideaId,
                        ConvertIdeaRequest(dayId = day.id)
                    )
                }
            }.onSuccess {
                addedDays[ideaId] = day.dayNumber
                _state.update { current ->
                    current.copy(
                        ideas = current.ideas.map { idea ->
                            if (idea.id == ideaId) idea.copy(addedDay = day.dayNumber) else idea
                        }
                    )
                }
                emit(TripIdeasEffect.ShowToastRes(R.string.ideas_added_to_itinerary_toast))
            }.onFailure {
                emit(TripIdeasEffect.ShowToastRes(R.string.common_error_message))
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
                    is CommentWsEvent.CommentDeleted -> handleCommentDeleted(event.payload)
                    is CommentWsEvent.Error -> Unit
                }
            }
        }
    }

    private fun handleCommentCreated(payload: CommentCreatedPayload) {
        _state.update { current ->
            var changed = false
            val updated = current.ideas.map { idea ->
                if (idea.id == payload.ideaId) {
                    changed = true
                    idea.copy(commentsCount = idea.commentsCount + 1)
                } else {
                    idea
                }
            }
            if (!changed) current else current.copy(ideas = updated)
        }
    }

    private fun handleCommentDeleted(payload: CommentDeletedPayload) {
        _state.update { current ->
            var changed = false
            val updated = current.ideas.map { idea ->
                if (idea.id == payload.ideaId) {
                    changed = true
                    idea.copy(commentsCount = (idea.commentsCount - 1).coerceAtLeast(0))
                } else {
                    idea
                }
            }
            if (!changed) current else current.copy(ideas = updated)
        }
    }

    private fun emit(effect: TripIdeasEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
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
