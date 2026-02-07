package nvk.cotrip.ui.tripform

sealed interface TripFormEffect {
    data class ShowToastRes(val resId: Int) : TripFormEffect
}
