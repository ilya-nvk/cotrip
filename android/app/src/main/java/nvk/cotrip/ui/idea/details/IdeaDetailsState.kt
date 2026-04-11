package nvk.cotrip.ui.idea.details

import nvk.cotrip.ui.idea.common.IdeaDayPickerState

data class IdeaDetailsState(
    val tripId: String,
    val ideaId: String,
    val title: String,
    val city: String,
    val link: String,
    val cost: String,
    val notes: String,
    val status: String,
    val addedDay: Int?,
    val isOwner: Boolean,
    val isUpdatingStatus: Boolean,
    val selectedTab: IdeaDetailsTab,
    val isDiscussionAvailable: Boolean,
    val commentsCount: Int,
    val discussion: List<IdeaDiscussionItemUi>,
    val commentInput: String,
    val dayPicker: IdeaDayPickerState?,
)
