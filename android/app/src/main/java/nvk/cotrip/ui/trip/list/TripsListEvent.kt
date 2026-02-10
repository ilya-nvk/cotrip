package nvk.cotrip.ui.trip.list

sealed interface TripsListEvent {
    data object OnSettingsClick : TripsListEvent
    data object OnCreateTripClick : TripsListEvent
    data object OnJoinTripClick : TripsListEvent
    data object OnAutoRefresh : TripsListEvent
    data object OnUserRefresh : TripsListEvent
    data class OnTripClick(val id: String) : TripsListEvent
    data object OnTogglePast : TripsListEvent
}
