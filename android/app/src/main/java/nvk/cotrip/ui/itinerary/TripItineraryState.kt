package nvk.cotrip.ui.itinerary

data class TripItineraryState(
    val tripId: String,
    val dateRange: String,
    val isPastTrip: Boolean,
    val mode: ItineraryMode,
    val days: List<ItineraryDayUi>,
    val cityPicker: CityPickerState?,
    val isCitySelectionRequired: Boolean,
    val pendingCitySelectionCount: Int,
    val isRefreshing: Boolean = false,
)
