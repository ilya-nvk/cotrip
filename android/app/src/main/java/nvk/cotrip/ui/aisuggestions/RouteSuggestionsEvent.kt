package nvk.cotrip.ui.aisuggestions

sealed interface RouteSuggestionsEvent {
    data object OnBackClick : RouteSuggestionsEvent
    data object OnRefreshClick : RouteSuggestionsEvent
    data object OnChangeFiltersClick : RouteSuggestionsEvent
    data class OnSaveClick(val suggestionId: String) : RouteSuggestionsEvent
}
