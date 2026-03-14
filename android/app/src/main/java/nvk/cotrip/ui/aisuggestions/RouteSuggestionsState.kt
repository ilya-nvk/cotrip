package nvk.cotrip.ui.aisuggestions

sealed interface RouteSuggestionsState {
    data class Loading(
        val tripId: String,
        val city: String,
        val subtitle: String,
    ) : RouteSuggestionsState

    data class Content(
        val tripId: String,
        val city: String,
        val subtitle: String,
        val suggestions: List<AiSuggestionItemUi>,
    ) : RouteSuggestionsState
}
