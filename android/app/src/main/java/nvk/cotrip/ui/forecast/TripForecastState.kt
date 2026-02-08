package nvk.cotrip.ui.forecast

data class TripForecastState(
    val city: String,
    val days: List<ForecastDayUi>,
    val source: String,
    val lastUpdated: String,
)

