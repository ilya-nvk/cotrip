package nvk.cotrip.ui.tripdetails

sealed interface TripDetailsEffect {
    data class ShowToastRes(val resId: Int) : TripDetailsEffect
}
