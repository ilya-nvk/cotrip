package nvk.cotrip.ui.outofrangedays

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class OutOfRangeDaysViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.OutOfRangeDays.ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        OutOfRangeDaysState(
            tripId = tripId,
            dateRangeText = "",
            days = emptyList(),
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<OutOfRangeDaysEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadOutOfRangeDays()
    }

    fun onEvent(event: OutOfRangeDaysEvent) {
        when (event) {
            OutOfRangeDaysEvent.OnBackClick -> appNavigator.popBackStack()
            OutOfRangeDaysEvent.OnKeepClick -> {
                trimOutOfRange(action = "keep")
            }

            OutOfRangeDaysEvent.OnRemoveClick -> {
                trimOutOfRange(action = "remove")
            }
        }
    }

    private fun loadOutOfRangeDays() {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val trip = tripRepository.getTrip(tripId)
                    val itinerary = itineraryRepository.getItinerary(tripId)
                    LoadedOutOfRange(
                        tripStart = LocalDate.parse(trip.startDate),
                        tripEnd = LocalDate.parse(trip.endDate),
                        days = itinerary
                    )
                }
            }

            result.onSuccess { loaded ->
                val outOfRange = loaded.days.filter { day ->
                    val date = LocalDate.parse(day.date)
                    date.isBefore(loaded.tripStart) || date.isAfter(loaded.tripEnd)
                }

                val rangeText = formatRange(loaded.tripStart, loaded.tripEnd)
                _state.value = OutOfRangeDaysState(
                    tripId = tripId,
                    dateRangeText = rangeText,
                    days = outOfRange.map { it.toUi() }
                )
            }.onFailure {
                emitToast(R.string.common_error_message)
                appNavigator.popBackStack()
            }
        }
    }

    private fun trimOutOfRange(action: String) {
        val ids = state.value.days.map { it.id }
        if (ids.isEmpty()) {
            appNavigator.popBackStack()
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    itineraryRepository.trimOutOfRange(
                        tripId = tripId,
                        request = TrimOutOfRangeRequest(
                            action = action,
                            dayIds = ids,
                        )
                    )
                }
            }

            result.onSuccess {
                val toast = if (action == "keep") {
                    R.string.out_of_range_days_kept_toast
                } else {
                    R.string.out_of_range_days_removed_toast
                }
                emitToast(toast)
                appNavigator.popBackStack()
            }.onFailure {
                emitToast(R.string.common_error_message)
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
    val dateText = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    val activities = activities.sortedBy { it.orderIndex }
    val preview = activities.take(2).map { it.title }.toMutableList()
    if (activities.size > 2) {
        preview += "and ${activities.size - 2} more…"
    }
    val activitiesTitle = when (activities.size) {
        0 -> "No activities"
        1 -> "1 activity"
        else -> "${activities.size} activities"
    }

    return OutOfRangeDayUi(
        id = id,
        dayTitle = "Day $dayNumber",
        dateText = dateText,
        city = city,
        activitiesTitle = activitiesTitle,
        activitiesPreview = preview,
    )
}

private fun formatRange(start: LocalDate, end: LocalDate): String {
    val locale = Locale.getDefault()
    val startText = start.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    val endText = end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    return "$startText – $endText"
}
