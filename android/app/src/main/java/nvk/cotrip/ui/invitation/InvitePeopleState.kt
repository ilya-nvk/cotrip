package nvk.cotrip.ui.invitation

sealed interface InvitePeopleState {
    data object Loading : InvitePeopleState

    data class Content(
        val tripId: String,
        val inviteLink: String,
        val expiresInHours: Int,
    ) : InvitePeopleState
}
