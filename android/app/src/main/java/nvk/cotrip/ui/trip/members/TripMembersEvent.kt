package nvk.cotrip.ui.trip.members

sealed interface TripMembersEvent {
    data object OnBackClick : TripMembersEvent
    data object OnRefresh : TripMembersEvent
    data class OnRemoveClick(val memberId: String) : TripMembersEvent
}
