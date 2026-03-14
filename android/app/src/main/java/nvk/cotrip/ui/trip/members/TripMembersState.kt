package nvk.cotrip.ui.trip.members

sealed interface TripMembersState {
    data object Loading : TripMembersState

    data class Content(
        val tripId: String,
        val title: String,
        val members: List<TripMemberUi>,
        val meId: String?,
        val isOwner: Boolean,
        val isLoadingAction: Boolean,
    ) : TripMembersState
}

data class TripMemberUi(
    val userId: String,
    val name: String,
    val photoUrl: String?,
    val initials: String,
    val role: String,
    val status: String,
)
