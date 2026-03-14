package nvk.cotrip.ui.trip.details

data class NextInTripUi(
    val subtitle: String,
    val lines: List<NextInTripLineUi>,
)

data class NextInTripLineUi(
    val time: String?,
    val title: String,
)
