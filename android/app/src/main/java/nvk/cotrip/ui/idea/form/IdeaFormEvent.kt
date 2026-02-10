package nvk.cotrip.ui.idea.form

sealed interface IdeaFormEvent {
    data object OnBackClick : IdeaFormEvent
    data object OnPrimaryClick : IdeaFormEvent
    data object OnDeleteClick : IdeaFormEvent
    data class OnCitySelected(val city: IdeaLocationSuggestionUi) : IdeaFormEvent
    data class OnTitleChange(val value: String) : IdeaFormEvent
    data class OnCityChange(val value: String) : IdeaFormEvent
    data class OnCostAmountChange(val value: String) : IdeaFormEvent
    data class OnCostTypeChange(val value: IdeaCostType) : IdeaFormEvent
    data class OnWebsiteChange(val value: String) : IdeaFormEvent
    data class OnNotesChange(val value: String) : IdeaFormEvent
}
