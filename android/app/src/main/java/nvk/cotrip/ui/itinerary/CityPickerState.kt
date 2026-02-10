package nvk.cotrip.ui.itinerary

data class CitySuggestionUi(
    val name: String,
    val placeId: String? = null,
    val fullText: String? = null,
)

data class CityPickerState(
    val dayId: String,
    val query: String,
    val localSuggestions: List<CitySuggestionUi>,
    val suggestions: List<CitySuggestionUi>,
    val isSearching: Boolean,
)
