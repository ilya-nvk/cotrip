package nvk.cotrip.ui.forecast

data class WeatherCityOption(
    val key: String,
    val label: String,
)

sealed interface TripForecastState {
    data object Loading : TripForecastState

    data class Content(
        /** Label shown in the UI (may be localized via `displayCity` from API). */
        val city: String,
        /** City string used for weather cache/API requests (itinerary key). */
        val weatherCityKey: String,
        val cityOptions: List<WeatherCityOption> = emptyList(),
        val isCityPickerVisible: Boolean = false,
        val days: List<ForecastDayUi>,
        val source: String,
        val lastUpdated: String,
        val coverageMessage: String? = null,
        val isRefreshing: Boolean = false,
    ) : TripForecastState
}
