package nvk.cotrip.ui.activitydetails

sealed interface ActivityDetailsEvent {
    data object OnBackClick : ActivityDetailsEvent
    data object OnEditClick : ActivityDetailsEvent
    data object OnOpenLocationClick : ActivityDetailsEvent
    data object OnOpenWebsiteClick : ActivityDetailsEvent
    data object OnDeleteClick : ActivityDetailsEvent
}
