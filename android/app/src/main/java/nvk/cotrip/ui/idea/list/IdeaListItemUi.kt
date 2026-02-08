package nvk.cotrip.ui.idea.list

data class IdeaListItemUi(
    val id: String,
    val title: String,
    val city: String,
    val cost: String?,
    val commentsCount: Int,
    val addedDay: Int?,
)
