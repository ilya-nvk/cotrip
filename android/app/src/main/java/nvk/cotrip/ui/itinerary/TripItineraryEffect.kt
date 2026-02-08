package nvk.cotrip.ui.itinerary

sealed interface TripItineraryEffect {
    data class ShowToastRes(val resId: Int) : TripItineraryEffect
}
