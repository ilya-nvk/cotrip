package nvk.cotrip.ui.activity.form

import java.time.LocalDate
import java.time.LocalTime

sealed interface ActivityFormEvent {
    data object OnBackClick : ActivityFormEvent
    data object OnPrimaryClick : ActivityFormEvent
    data object OnDeleteClick : ActivityFormEvent
    data object OnPickDateClick : ActivityFormEvent
    data object OnPickTimeClick : ActivityFormEvent
    data class OnDateSelected(val date: LocalDate) : ActivityFormEvent
    data class OnTimeSelected(val time: LocalTime) : ActivityFormEvent

    data class OnTitleChange(val value: String) : ActivityFormEvent
    data class OnLocationInputChange(val value: String) : ActivityFormEvent
    data class OnLocationSuggestionSelected(val value: LocationSuggestionUi) : ActivityFormEvent
    data class OnLinkChange(val value: String) : ActivityFormEvent
    data class OnCostAmountChange(val value: String) : ActivityFormEvent
    data class OnCostTypeChange(val value: CostType) : ActivityFormEvent
    data class OnNotesChange(val value: String) : ActivityFormEvent
}
