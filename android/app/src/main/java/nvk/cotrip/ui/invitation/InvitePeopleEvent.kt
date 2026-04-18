package nvk.cotrip.ui.invitation

sealed interface InvitePeopleEvent {
    data object OnCloseClick : InvitePeopleEvent
    data object OnRetryClick : InvitePeopleEvent
    data object OnCopyClick : InvitePeopleEvent
    data object OnShareClick : InvitePeopleEvent
}
