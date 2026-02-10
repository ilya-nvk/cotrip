package nvk.cotrip.ui.forecast

sealed interface TripForecastEvent {
    data object OnBackClick : TripForecastEvent
    data object OnAutoRefresh : TripForecastEvent
    data object OnUserRefresh : TripForecastEvent
    data object OnCityClick : TripForecastEvent
}
