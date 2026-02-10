package nvk.cotrip.ui.itinerary

sealed interface TripItineraryEvent {
    data object OnBackClick : TripItineraryEvent
    data object OnCompleteRequiredCitySelection : TripItineraryEvent
    data object OnAutoRefresh : TripItineraryEvent
    data object OnUserRefresh : TripItineraryEvent
    data object OnToggleReorder : TripItineraryEvent
    data object OnDismissCityPicker : TripItineraryEvent
    data object OnAddActivityClick : TripItineraryEvent
    data class OnActivityClick(val activityId: String) : TripItineraryEvent
    data class OnChooseCityClick(val dayId: String) : TripItineraryEvent
    data class OnCityQueryChange(val value: String) : TripItineraryEvent
    data class OnCitySelected(val city: CitySuggestionUi) : TripItineraryEvent
    data class OnReorderMove(
        val dayId: String,
        val fromIndex: Int,
        val toIndex: Int,
    ) : TripItineraryEvent
    data class OnReorderCommit(val dayId: String) : TripItineraryEvent
}
