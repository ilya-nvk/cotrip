package nvk.cotrip.ui.trips

sealed interface TripsListEvent {
    data object OnSettingsClick : TripsListEvent
    data object OnCreateTripClick : TripsListEvent
    data class OnTripClick(val id: String) : TripsListEvent
    data object OnTogglePast : TripsListEvent
}