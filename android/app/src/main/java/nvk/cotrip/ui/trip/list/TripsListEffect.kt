package nvk.cotrip.ui.trip.list

sealed interface TripsListEffect {
    data class ShowToast(val message: String) : TripsListEffect
}