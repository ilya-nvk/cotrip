package nvk.cotrip.ui.trips

sealed interface TripsListUiState {
    data object Loading : TripsListUiState
    data class Content(
        val activeTrips: List<TripCardUi> = emptyList(),
        val upcomingTrips: List<TripCardUi> = emptyList(),
        val pastTrips: List<TripCardUi> = emptyList(),
        val showPastTrips: Boolean = false,
    ) : TripsListUiState
}