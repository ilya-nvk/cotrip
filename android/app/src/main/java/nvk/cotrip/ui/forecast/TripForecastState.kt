package nvk.cotrip.ui.forecast

data class TripForecastState(
    val city: String,
    val cityOptions: List<String> = emptyList(),
    val isCityPickerVisible: Boolean = false,
    val days: List<ForecastDayUi>,
    val source: String,
    val lastUpdated: String,
    val coverageMessage: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)
