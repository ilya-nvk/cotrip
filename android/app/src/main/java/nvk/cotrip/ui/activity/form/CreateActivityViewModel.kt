package nvk.cotrip.ui.activity.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class CreateActivityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel(), ActivityFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.CreateActivity.ARG_TRIP_ID))

    private val _state = MutableStateFlow(
        ActivityFormState(
            mode = ActivityFormMode.Create,
            activityId = null,
            headerText = null,
            title = "",
            dateText = "16.07.2026",
            timeText = "",
            locationName = "",
            locationLink = "",
            currencySymbol = "€",
            costAmount = "",
            costType = CostType.PerPerson,
            website = "",
            notes = "",
            isSaving = false
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ActivityFormEffect>()
    override val effects = _effects.asSharedFlow()

    override fun onEvent(event: ActivityFormEvent) {
        when (event) {
            ActivityFormEvent.OnBackClick -> appNavigator.popBackStack()
            ActivityFormEvent.OnPrimaryClick -> {
                emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_created_toast))
                appNavigator.popBackStack()
            }

            ActivityFormEvent.OnDeleteClick -> emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_delete_not_available))
            ActivityFormEvent.OnPickDateClick -> emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_pick_date_not_implemented))
            ActivityFormEvent.OnPickTimeClick -> emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_pick_time_not_implemented))
            is ActivityFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is ActivityFormEvent.OnLocationNameChange -> _state.update { it.copy(locationName = event.value) }
            is ActivityFormEvent.OnLocationLinkChange -> _state.update { it.copy(locationLink = event.value) }
            is ActivityFormEvent.OnCostAmountChange -> _state.update { it.copy(costAmount = event.value.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
            is ActivityFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is ActivityFormEvent.OnWebsiteChange -> _state.update { it.copy(website = event.value) }
            is ActivityFormEvent.OnNotesChange -> _state.update { it.copy(notes = event.value) }
        }
    }

    private fun emit(effect: ActivityFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}