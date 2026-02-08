package nvk.cotrip.ui.trip.details

sealed interface TripDetailsEvent {
    data object OnBackClick : TripDetailsEvent
    data object OnEditClick : TripDetailsEvent
    data object OnInviteTravelersClick : TripDetailsEvent
    data object OnWeatherCityClick : TripDetailsEvent
    data object OnViewForecastClick : TripDetailsEvent
    data object OnViewItineraryClick : TripDetailsEvent
    data object OnBrowseIdeasClick : TripDetailsEvent
    data object OnIdeasClick : TripDetailsEvent
    data object OnExpensesClick : TripDetailsEvent
    data object OnPrimaryCtaClick : TripDetailsEvent
}
