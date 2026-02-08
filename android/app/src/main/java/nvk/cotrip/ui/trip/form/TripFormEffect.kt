package nvk.cotrip.ui.trip.form

sealed interface TripFormEffect {
    data class ShowToastRes(val resId: Int) : TripFormEffect
}
