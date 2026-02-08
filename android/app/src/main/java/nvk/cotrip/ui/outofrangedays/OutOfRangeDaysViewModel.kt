package nvk.cotrip.ui.outofrangedays

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class OutOfRangeDaysViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.OutOfRangeDays.ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        OutOfRangeDaysState(
            tripId = tripId,
            dateRangeText = "Invalid Date – Invalid Date",
            days = listOf(
                OutOfRangeDayUi(
                    id = "13",
                    dayTitle = "Day 13",
                    dateText = "Fri, Jul 27",
                    city = "Nice",
                    activitiesTitle = "4 activities",
                    activitiesPreview = listOf(
                        "Morning beach walk",
                        "Visit Promenade des Anglais",
                        "and 2 more…"
                    )
                ),
                OutOfRangeDayUi(
                    id = "14",
                    dayTitle = "Day 14",
                    dateText = "Sat, Jul 28",
                    city = "Nice",
                    activitiesTitle = "2 activities",
                    activitiesPreview = listOf(
                        "Day trip to Monaco",
                        "Return to Paris"
                    )
                ),
                OutOfRangeDayUi(
                    id = "15",
                    dayTitle = "Day 15",
                    dateText = "Sun, Jul 29",
                    city = null,
                    activitiesTitle = "1 activity",
                    activitiesPreview = listOf(
                        "Flight home"
                    )
                ),
            )
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<OutOfRangeDaysEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: OutOfRangeDaysEvent) {
        when (event) {
            OutOfRangeDaysEvent.OnBackClick -> appNavigator.popBackStack()
            OutOfRangeDaysEvent.OnKeepClick -> {
                emitToast(R.string.out_of_range_days_kept_toast)
                appNavigator.popBackStack()
            }

            OutOfRangeDaysEvent.OnRemoveClick -> {
                emitToast(R.string.out_of_range_days_removed_toast)
                appNavigator.popBackStack()
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(OutOfRangeDaysEffect.ShowToastRes(resId)) }
    }
}
