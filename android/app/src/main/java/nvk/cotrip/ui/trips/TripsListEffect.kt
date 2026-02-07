package nvk.cotrip.ui.trips

sealed interface TripsListEffect {
    data class ShowToast(val message: String) : TripsListEffect
}