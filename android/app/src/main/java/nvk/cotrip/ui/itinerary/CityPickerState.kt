package nvk.cotrip.ui.itinerary

data class CityPickerState(
    val dayId: String,
    val query: String,
    val allCities: List<String>,
    val filteredCities: List<String>,
)
