package nvk.cotrip.ui.activity.details

sealed interface ActivityDetailsEvent {
    data object OnBackClick : ActivityDetailsEvent
    data object OnRefresh : ActivityDetailsEvent
    data object OnEditClick : ActivityDetailsEvent
    data object OnOpenLinkClick : ActivityDetailsEvent
    data object OnOpenWebsiteClick : ActivityDetailsEvent
    data object OnDeleteClick : ActivityDetailsEvent
}
