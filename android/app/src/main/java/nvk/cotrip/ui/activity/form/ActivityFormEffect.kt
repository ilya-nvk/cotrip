package nvk.cotrip.ui.activity.form

sealed interface ActivityFormEffect {
    data class ShowToastRes(val resId: Int) : ActivityFormEffect
}
