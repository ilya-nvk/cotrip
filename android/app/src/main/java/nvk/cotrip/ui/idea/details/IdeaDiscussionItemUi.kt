package nvk.cotrip.ui.idea.details

sealed interface IdeaDiscussionItemUi {
    val id: String

    data class Message(
        override val id: String,
        val author: String,
        val initials: String,
        val text: String,
        val time: String,
        val isMe: Boolean,
    ) : IdeaDiscussionItemUi

    data class System(
        override val id: String,
        val text: String,
        val time: String,
    ) : IdeaDiscussionItemUi
}
