package nvk.cotrip.ui.forecast

sealed interface TripForecastEvent {
    data object OnBackClick : TripForecastEvent
    data object OnCityClick : TripForecastEvent
}
