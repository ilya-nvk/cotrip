package nvk.cotrip.ui.aisuggestions

data class RouteSuggestionsState(
    val tripId: String,
    val city: String,
    val subtitle: String,
    val isLoading: Boolean,
    val suggestions: List<AiSuggestionItemUi>,
)
