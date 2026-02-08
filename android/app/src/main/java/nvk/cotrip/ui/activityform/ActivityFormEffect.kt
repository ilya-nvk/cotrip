package nvk.cotrip.ui.activityform

sealed interface ActivityFormEffect {
    data class ShowToastRes(val resId: Int) : ActivityFormEffect
}
