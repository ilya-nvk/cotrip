package nvk.cotrip.ui.idea.list

import nvk.cotrip.ui.idea.common.IdeaDayPickerState

sealed interface TripIdeasState {
    data object Loading : TripIdeasState

    data class Content(
        val tripId: String,
        val ideas: List<IdeaListItemUi>,
        val dayPicker: IdeaDayPickerState?,
        val isRefreshing: Boolean = false,
    ) : TripIdeasState
}
