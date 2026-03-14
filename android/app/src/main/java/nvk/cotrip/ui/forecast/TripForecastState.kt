package nvk.cotrip.ui.forecast

sealed interface TripForecastState {
    data object Loading : TripForecastState

    data class Content(
        val city: String,
        val cityOptions: List<String> = emptyList(),
        val isCityPickerVisible: Boolean = false,
        val days: List<ForecastDayUi>,
        val source: String,
        val lastUpdated: String,
        val coverageMessage: String? = null,
        val isRefreshing: Boolean = false,
    ) : TripForecastState
}
