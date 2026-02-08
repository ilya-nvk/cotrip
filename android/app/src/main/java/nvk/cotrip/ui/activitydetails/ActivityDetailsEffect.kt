package nvk.cotrip.ui.activitydetails

sealed interface ActivityDetailsEffect {
    data class ShowToastRes(val resId: Int) : ActivityDetailsEffect
}
