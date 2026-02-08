package nvk.cotrip.ui.idea.details

import nvk.cotrip.ui.idea.common.IdeaDayOptionUi

sealed interface IdeaDetailsEvent {
    data object OnBackClick : IdeaDetailsEvent
    data object OnEditClick : IdeaDetailsEvent
    data object OnAddToItineraryClick : IdeaDetailsEvent
    data object OnDeleteClick : IdeaDetailsEvent
    data object OnDismissDayPicker : IdeaDetailsEvent
    data class OnDaySelected(val day: IdeaDayOptionUi) : IdeaDetailsEvent
    data class OnTabSelected(val tab: IdeaDetailsTab) : IdeaDetailsEvent
    data class OnCommentChange(val value: String) : IdeaDetailsEvent
    data object OnSendComment : IdeaDetailsEvent
}
