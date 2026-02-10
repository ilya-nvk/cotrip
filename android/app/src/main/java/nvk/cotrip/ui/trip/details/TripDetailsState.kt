package nvk.cotrip.ui.trip.details

import nvk.cotrip.ui.components.AvatarStackItem

data class TripDetailsState(
    val isEmpty: Boolean,
    val isOwner: Boolean,
    val header: TripHeaderUi,
    val travelers: List<AvatarStackItem>,
    val peopleCountText: String,
    val weather: WeatherCardUi,
    val nextInTrip: NextInTripUi,
    val overview: TripOverviewUi,
    val isRefreshing: Boolean = false,
)
