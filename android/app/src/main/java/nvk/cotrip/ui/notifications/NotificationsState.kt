package nvk.cotrip.ui.notifications

data class NotificationsState(
    val isLoading: Boolean,
    val items: List<NotificationItemUi>,
)

data class NotificationItemUi(
    val id: String,
    val title: String,
    val subtitle: String?,
    val timestamp: String,
    val isRead: Boolean,
)
