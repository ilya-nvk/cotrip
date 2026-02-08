package nvk.cotrip.ui.activity.form

sealed interface ActivityFormEvent {
    data object OnBackClick : ActivityFormEvent
    data object OnPrimaryClick : ActivityFormEvent
    data object OnDeleteClick : ActivityFormEvent
    data object OnPickDateClick : ActivityFormEvent
    data object OnPickTimeClick : ActivityFormEvent

    data class OnTitleChange(val value: String) : ActivityFormEvent
    data class OnLocationNameChange(val value: String) : ActivityFormEvent
    data class OnLocationLinkChange(val value: String) : ActivityFormEvent
    data class OnCostAmountChange(val value: String) : ActivityFormEvent
    data class OnCostTypeChange(val value: CostType) : ActivityFormEvent
    data class OnWebsiteChange(val value: String) : ActivityFormEvent
    data class OnNotesChange(val value: String) : ActivityFormEvent
}
