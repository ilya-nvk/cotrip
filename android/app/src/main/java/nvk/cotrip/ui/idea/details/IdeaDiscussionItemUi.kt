package nvk.cotrip.ui.idea.details

sealed interface IdeaDiscussionItemUi {
    val id: String

    enum class DeliveryState {
        Sent,
        Sending,
        Failed,
    }

    data class Message(
        override val id: String,
        val author: String,
        val initials: String,
        val photoUrl: String?,
        val text: String,
        val time: String,
        val isMe: Boolean,
        val deliveryState: DeliveryState = DeliveryState.Sent,
        val localId: String? = null,
        val deleteOldestOnRetry: Boolean = false,
    ) : IdeaDiscussionItemUi

    data class System(
        override val id: String,
        val text: String,
        val time: String,
    ) : IdeaDiscussionItemUi
}
