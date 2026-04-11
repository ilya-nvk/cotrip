package nvk.cotrip.ui.activity.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ActivityDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val activityId: String =
        (savedStateHandle.get<String>(Destination.ActivityDetails.ARG_ACTIVITY_ID)).orEmpty()

    private val _state: MutableStateFlow<ActivityDetailsState> = MutableStateFlow(
        ActivityDetailsState.Init(activityId = activityId)
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ActivityDetailsEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()
    private var currentTripId: String? = null

    init {
        observeActivity()
        refreshActivity(showErrorToast = false)
    }

    fun onEvent(event: ActivityDetailsEvent) {
        when (event) {
            ActivityDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            ActivityDetailsEvent.OnRefresh -> refreshActivity(showErrorToast = true)
            ActivityDetailsEvent.OnEditClick -> {
                val contentState = _state.value as? ActivityDetailsState.Content ?: return
                if (!contentState.isPastTrip) {
                    appNavigator.navigate(
                        Destination.EditActivity(activityId)
                    )
                }
            }

            ActivityDetailsEvent.OnOpenLinkClick -> {
                val contentState = _state.value as? ActivityDetailsState.Content ?: return
                val link = contentState.link?.trim().orEmpty()
                if (link.isNotBlank()) {
                    val normalized = if (link.startsWith("http://") || link.startsWith("https://")) {
                        link
                    } else {
                        "https://$link"
                    }
                    emit(ActivityDetailsEffect.OpenExternalLink(normalized))
                }
            }
            ActivityDetailsEvent.OnDeleteClick -> deleteActivity()
        }
    }

    private fun observeActivity() {
        viewModelScope.launch {
            buildActivityFlow(activityId).collect { lookup ->
                if (lookup == null) {
                    currentTripId = null
                    _state.value = ActivityDetailsState.Init(activityId = activityId)
                } else {
                    currentTripId = lookup.trip.id
                    _state.value = lookup.toUiState()
                }
            }
        }
    }

    private fun refreshActivity(showErrorToast: Boolean) {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                tripRepository.refreshTrips().getOrThrow()
                val tripIds = tripRepository.trips.first().map { it.id }
                tripIds.forEach { tripId ->
                    itineraryRepository.refreshItinerary(tripId).getOrThrow()
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> if (showErrorToast) emitToast(
                    uiErrorMapper.messageRes(
                        result
                    )
                )
            }
        }
    }

    private fun deleteActivity() {
        val contentState = _state.value as? ActivityDetailsState.Content ?: return
        if (contentState.isPastTrip) return
        if (currentTripId == null) return
        viewModelScope.launch {
            when (val result = apiCaller.call {
                itineraryRepository.deleteActivity(activityId)
            }) {
                is ApiResult.Success -> {
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> emitToast(uiErrorMapper.messageRes(result))
            }
        }
    }

    private fun buildActivityFlow(activityId: String): Flow<ActivityLookup?> {
        return tripRepository.trips.flatMapLatest { trips ->
            if (trips.isEmpty()) {
                flowOf(null)
            } else {
                val itineraryFlows = trips.map { trip ->
                    itineraryRepository.observeItinerary(trip.id).map { days -> trip to days }
                }
                combine(itineraryFlows) { tripAndDaysArray ->
                    tripAndDaysArray.asList()
                        .firstNotNullOfOrNull { (trip, days) ->
                            val day = days.firstOrNull { itineraryDay ->
                                itineraryDay.activities.any { activity -> activity.id == activityId }
                            } ?: return@firstNotNullOfOrNull null
                            val activity = day.activities.firstOrNull { it.id == activityId }
                                ?: return@firstNotNullOfOrNull null
                            ActivityLookup(trip = trip, day = day, activity = activity)
                        }
                }
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(ActivityDetailsEffect.ShowToastRes(resId)) }
    }

    private fun emit(effect: ActivityDetailsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private fun ActivityLookup.toUiState(): ActivityDetailsState.Content {
        val currencySymbol = currencySymbolFor(trip.currencyCode)
        val isPastTrip = runCatching {
            LocalDate.parse(trip.endDate).isBefore(LocalDate.now())
        }.getOrDefault(false)
        return ActivityDetailsState.Content(
            dayId = day.id,
            activityId = activity.id,
            isPastTrip = isPastTrip,
            dayNumber = day.dayNumber,
            city = day.city?.takeIf { it.isNotBlank() },
            title = activity.title,
            dateText = formatDay(day.date),
            timeText = activity.timeText.orEmpty(),
            locationName = activity.locationName,
            link = activity.link,
            costText = activity.costAmount?.let { formatCost(it, currencySymbol) },
            notes = activity.notes,
        )
    }

    private data class ActivityLookup(
        val trip: TripDto,
        val day: ItineraryDayDto,
        val activity: ActivityDto,
    )
}

private fun formatDay(date: String): String {
    val parsed = LocalDate.parse(date)
    return parsed.format(DateTimeFormatter.ofPattern("EEE, MMM d", appUiLocale()))
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}

private fun formatCost(amount: Double, currencySymbol: String): String {
    val display = if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(appUiLocale(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}
