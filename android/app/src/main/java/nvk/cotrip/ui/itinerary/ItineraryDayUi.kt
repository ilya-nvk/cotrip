package nvk.cotrip.ui.itinerary

data class ItineraryDayUi(
    val id: String,
    val dayNumber: Int,
    val dateIso: String,
    val dateText: String,
    val city: String?,
    val activities: List<ItineraryActivityUi>,
)
