package nvk.cotrip.ui.itinerary

import nvk.cotrip.ui.common.appUiLocale

data class CitySuggestionUi(
    val name: String,
    val providerId: String? = null,
    val lat: Double,
    val lon: Double,
    val fullText: String? = null,
)

data class CityPickerState(
    val dayId: String,
    val dayNumber: Int,
    val dayDate: String,
    val query: String,
    val localSuggestions: List<CitySuggestionUi>,
    val suggestions: List<CitySuggestionUi>,
    val isSearching: Boolean,
)

internal fun CitySuggestionUi.visibleCityPickerDedupeKey(): String {
    val visible = fullText?.trim()?.takeIf { it.isNotEmpty() } ?: name.trim()
    return visible.lowercase(appUiLocale())
}
