package nvk.cotrip.ui.itinerary

sealed interface TripItineraryEvent {
    data object OnBackClick : TripItineraryEvent
    data object OnDismissCityPicker : TripItineraryEvent
    data object OnAddActivityClick : TripItineraryEvent
    data class OnActivityClick(val activityId: String) : TripItineraryEvent
    data class OnChooseCityClick(val dayId: String) : TripItineraryEvent
    data class OnCityQueryChange(val value: String) : TripItineraryEvent
    data class OnCitySelected(val city: String) : TripItineraryEvent
}
