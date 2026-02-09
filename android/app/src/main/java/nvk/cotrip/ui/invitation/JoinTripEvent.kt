package nvk.cotrip.ui.invitation

sealed interface JoinTripEvent {
    data object OnBackClick : JoinTripEvent
    data class OnInviteInputChange(val value: String) : JoinTripEvent
    data object OnJoinClick : JoinTripEvent
}
