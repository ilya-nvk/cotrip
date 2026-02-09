package nvk.cotrip.ui.invitation

sealed interface JoinTripEffect {
    data class ShowToastRes(val resId: Int) : JoinTripEffect
}
