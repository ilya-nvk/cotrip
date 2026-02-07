package nvk.cotrip.ui.tripdetails

data class TripDetailsState(
    val isEmpty: Boolean,
    val header: TripHeaderUi,
    val travelers: List<String>,
    val peopleCountText: String,
    val weather: WeatherCardUi,
    val nextInTrip: NextInTripUi,
    val overview: TripOverviewUi,
)
