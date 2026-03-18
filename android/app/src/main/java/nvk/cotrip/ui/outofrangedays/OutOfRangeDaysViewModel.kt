package nvk.cotrip.ui.outofrangedays

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class OutOfRangeDaysViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.OutOfRangeDays.ARG_TRIP_ID])

    private val _state = MutableStateFlow<OutOfRangeDaysState>(OutOfRangeDaysState.Loading)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<OutOfRangeDaysEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        loadOutOfRangeDays()
    }

    fun onEvent(event: OutOfRangeDaysEvent) {
        when (event) {
            OutOfRangeDaysEvent.OnBackClick -> appNavigator.popBackStack()
            OutOfRangeDaysEvent.OnExtendEndClick -> {
                trimOutOfRange(action = "extend_end")
            }

            OutOfRangeDaysEvent.OnRemoveClick -> {
                trimOutOfRange(action = "remove")
            }
        }
    }

    private fun loadOutOfRangeDays() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                val trip = tripRepository.getTrip(tripId).first()
                itineraryRepository.refreshItinerary(tripId).getOrThrow()
                val itinerary = itineraryRepository.getItinerary(tripId).first()
                LoadedOutOfRange(
                    tripStart = LocalDate.parse(trip.startDate),
                    tripEnd = LocalDate.parse(trip.endDate),
                    days = itinerary
                )
            }) {
                is ApiResult.Success -> {
                    val loaded = result.data
                    val outOfRange = loaded.days.filter { day -> day.isOutOfRange }

                    val rangeText = formatRange(loaded.tripStart, loaded.tripEnd)
                    val proposedEnd = loaded.tripEnd.plusDays(outOfRange.size.toLong())
                    _state.value = OutOfRangeDaysState.Content(
                        tripId = tripId,
                        dateRangeText = rangeText,
                        proposedEndDateText = formatDate(proposedEnd),
                        days = outOfRange.map { it.toUi() }
                    )
                }

                is ApiResult.Failure -> {
                    emitToast(uiErrorMapper.messageRes(result))
                    appNavigator.popBackStack()
                }
            }
        }
    }

    private fun trimOutOfRange(action: String) {
        val content = state.value as? OutOfRangeDaysState.Content ?: return
        val ids = content.days.map { it.id }
        if (ids.isEmpty()) {
            appNavigator.popBackStack()
            return
        }

        viewModelScope.launch {
            when (val result = apiCaller.call {
                itineraryRepository.trimOutOfRange(
                    tripId = tripId,
                    request = TrimOutOfRangeRequest(
                        action = action,
                        dayIds = ids,
                    )
                )
            }) {
                is ApiResult.Success -> {
                    val toast = when (action) {
                        "extend_end" -> R.string.out_of_range_days_extended_toast
                        "remove" -> R.string.out_of_range_days_removed_toast
                        else -> R.string.out_of_range_days_kept_toast
                    }
                    emitToast(toast)
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> emitToast(uiErrorMapper.messageRes(result))
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(OutOfRangeDaysEffect.ShowToastRes(resId)) }
    }
}

private data class LoadedOutOfRange(
    val tripStart: LocalDate,
    val tripEnd: LocalDate,
    val days: List<ItineraryDayDto>,
)

private fun ItineraryDayDto.toUi(): OutOfRangeDayUi {
    val date = LocalDate.parse(date)
    val dateText = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", appUiLocale()))
    val activities = activities.sortedBy { it.orderIndex }
    val preview = activities.take(2).map { it.title }
    val hiddenActivitiesCount = (activities.size - preview.size).coerceAtLeast(0)

    return OutOfRangeDayUi(
        id = id,
        dayNumber = dayNumber,
        dateText = dateText,
        city = city,
        activitiesCount = activities.size,
        activitiesPreview = preview,
        hiddenActivitiesCount = hiddenActivitiesCount,
    )
}

private fun formatRange(start: LocalDate, end: LocalDate): String {
    val locale = appUiLocale()
    val startText = start.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    val endText = end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    return "$startText – $endText"
}

private fun formatDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", appUiLocale()))
}
