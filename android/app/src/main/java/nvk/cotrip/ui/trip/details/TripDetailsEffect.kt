package nvk.cotrip.ui.trip.details

sealed interface TripDetailsEffect {
    data class ShowToastRes(val resId: Int) : TripDetailsEffect
}
