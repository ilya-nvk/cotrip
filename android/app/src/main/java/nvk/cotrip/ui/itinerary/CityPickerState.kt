package nvk.cotrip.ui.itinerary

data class CitySuggestionUi(
    val name: String,
    val providerId: String? = null,
    val lat: Double,
    val lon: Double,
    val fullText: String? = null,
)

data class CityPickerState(
    val dayId: String,
    val query: String,
    val localSuggestions: List<CitySuggestionUi>,
    val suggestions: List<CitySuggestionUi>,
    val isSearching: Boolean,
)
