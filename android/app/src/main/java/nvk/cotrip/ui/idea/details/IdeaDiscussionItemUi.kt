package nvk.cotrip.ui.idea.details

sealed interface IdeaDiscussionItemUi {
    data class Message(
        val id: String,
        val author: String,
        val initials: String,
        val text: String,
        val time: String,
        val isMe: Boolean,
    ) : IdeaDiscussionItemUi

    data class System(
        val id: String,
        val text: String,
        val time: String,
    ) : IdeaDiscussionItemUi
}
