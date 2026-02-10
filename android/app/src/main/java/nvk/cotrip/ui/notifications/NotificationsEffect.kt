package nvk.cotrip.ui.notifications

sealed interface NotificationsEffect {
    data class ShowToastRes(val resId: Int) : NotificationsEffect
}
