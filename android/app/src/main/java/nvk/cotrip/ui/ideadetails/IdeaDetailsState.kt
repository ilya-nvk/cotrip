package nvk.cotrip.ui.ideadetails

import nvk.cotrip.ui.ideas.IdeaDayPickerState

data class IdeaDetailsState(
    val tripId: String,
    val ideaId: String,
    val title: String,
    val city: String,
    val cost: String,
    val website: String,
    val notes: String,
    val addedDay: Int?,
    val selectedTab: IdeaDetailsTab,
    val commentsCount: Int,
    val discussion: List<IdeaDiscussionItemUi>,
    val commentInput: String,
    val dayPicker: IdeaDayPickerState?,
)
