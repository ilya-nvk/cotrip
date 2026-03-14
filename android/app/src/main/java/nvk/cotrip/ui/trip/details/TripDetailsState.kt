package nvk.cotrip.ui.trip.details

import nvk.cotrip.ui.components.AvatarStackItem

sealed interface TripDetailsState {
    data object Loading : TripDetailsState

    data class Content(
        val isEmpty: Boolean,
        val isPast: Boolean,
        val isOwner: Boolean,
        val header: TripHeaderUi,
        val travelers: List<AvatarStackItem>,
        val peopleCountText: String,
        val weather: WeatherCardUi,
        val nextInTrip: NextInTripUi,
        val overview: TripOverviewUi,
        val isRefreshing: Boolean = false,
    ) : TripDetailsState
}
