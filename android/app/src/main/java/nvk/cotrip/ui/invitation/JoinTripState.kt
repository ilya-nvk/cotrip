package nvk.cotrip.ui.invitation

data class JoinTripState(
    val inviteInput: String,
    val isLoading: Boolean,
    val isInviteValid: Boolean,
)
