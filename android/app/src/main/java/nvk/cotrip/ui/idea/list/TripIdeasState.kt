package nvk.cotrip.ui.idea.list

import nvk.cotrip.ui.idea.common.IdeaDayPickerState

data class TripIdeasState(
    val tripId: String,
    val ideas: List<IdeaListItemUi>,
    val dayPicker: IdeaDayPickerState?,
)
