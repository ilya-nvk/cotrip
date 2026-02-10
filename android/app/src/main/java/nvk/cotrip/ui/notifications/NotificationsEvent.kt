package nvk.cotrip.ui.notifications

sealed interface NotificationsEvent {
    data object OnBackClick : NotificationsEvent
    data object OnRefresh : NotificationsEvent
    data class OnNotificationClick(val id: String) : NotificationsEvent
}
