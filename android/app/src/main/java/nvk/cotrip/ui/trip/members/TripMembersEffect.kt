package nvk.cotrip.ui.trip.members

sealed interface TripMembersEffect {
    data class ShowToastRes(val resId: Int) : TripMembersEffect
}
