package nvk.cotrip.ui.activitydetails

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
class ActivityDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.ActivityDetails.ARG_TRIP_ID])
    private val activityId: String =
        (savedStateHandle.get<String>(Destination.ActivityDetails.ARG_ACTIVITY_ID)).orEmpty()

    private val _state = MutableStateFlow(
        ActivityDetailsState(
            tripId = tripId,
            dayId = "2",
            activityId = activityId.ifBlank { "a5" },
            dayAndCity = "Day 2 · Paris",
            title = "Visit the Louvre Museum",
            dateText = "Fri, Jul 16",
            timeText = "09:00",
            locationName = "Louvre Museum",
            costText = "€17",
            website = "https://louvre.fr",
            notes = "Book tickets in advance to skip the line. The museum is closed on Tuesdays. Allow at least 3-4 hours to see the highlights."
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ActivityDetailsEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: ActivityDetailsEvent) {
        when (event) {
            ActivityDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            ActivityDetailsEvent.OnEditClick -> emitToast(R.string.activity_details_edit_not_implemented)
            ActivityDetailsEvent.OnOpenLocationClick -> emitToast(R.string.activity_details_open_location_not_implemented)
            ActivityDetailsEvent.OnOpenWebsiteClick -> emitToast(R.string.activity_details_open_website_not_implemented)
            ActivityDetailsEvent.OnDeleteClick -> {
                emitToast(R.string.activity_details_deleted_toast)
                appNavigator.popBackStack()
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(ActivityDetailsEffect.ShowToastRes(resId)) }
    }
}