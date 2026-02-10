package nvk.cotrip.ui.trip.members

data class TripMembersState(
    val tripId: String,
    val title: String,
    val members: List<TripMemberUi>,
    val meId: String?,
    val isOwner: Boolean,
    val isLoading: Boolean,
)

data class TripMemberUi(
    val userId: String,
    val name: String,
    val photoUrl: String?,
    val initials: String,
    val role: String,
    val status: String,
)
