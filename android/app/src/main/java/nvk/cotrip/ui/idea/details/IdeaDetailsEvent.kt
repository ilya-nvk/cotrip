package nvk.cotrip.ui.idea.details

import nvk.cotrip.ui.idea.common.IdeaDayOptionUi

sealed interface IdeaDetailsEvent {
    data object OnBackClick : IdeaDetailsEvent
    /** Lifecycle / silent refresh: no error toast when network fails but cache is shown. */
    data object OnAutoRefresh : IdeaDetailsEvent
    /** Explicit user refresh (e.g. pull-to-refresh): may show error toast. */
    data object OnRefresh : IdeaDetailsEvent
    data object OnEditClick : IdeaDetailsEvent
    data object OnAddToItineraryClick : IdeaDetailsEvent
    data object OnDeleteClick : IdeaDetailsEvent
    data object OnApproveClick : IdeaDetailsEvent
    data object OnRejectClick : IdeaDetailsEvent
    data object OnDismissDayPicker : IdeaDetailsEvent
    data class OnDaySelected(val day: IdeaDayOptionUi) : IdeaDetailsEvent
    data class OnTabSelected(val tab: IdeaDetailsTab) : IdeaDetailsEvent
    data class OnCommentChange(val value: String) : IdeaDetailsEvent
    data object OnSendComment : IdeaDetailsEvent
    data class OnRetryComment(val localId: String) : IdeaDetailsEvent
    data class OnDeletePendingComment(val localId: String) : IdeaDetailsEvent
}
