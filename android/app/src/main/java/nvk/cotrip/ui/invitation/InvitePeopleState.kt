package nvk.cotrip.ui.invitation

data class InvitePeopleState(
    val tripId: String,
    val inviteLink: String,
    val expiresInHours: Int,
)
