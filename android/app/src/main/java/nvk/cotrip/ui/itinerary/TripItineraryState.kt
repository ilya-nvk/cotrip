package nvk.cotrip.ui.itinerary

data class TripItineraryState(
    val tripId: String,
    val dateRange: String,
    val mode: ItineraryMode,
    val days: List<ItineraryDayUi>,
    val cityPicker: CityPickerState?,
)
