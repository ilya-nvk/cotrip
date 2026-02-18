package nvk.cotrip.ui.trip.details

data class WeatherCardUi(
    val city: String,
    val days: List<WeatherDayUi>,
    val notice: WeatherCardNotice = WeatherCardNotice.None,
)

enum class WeatherCardNotice {
    None,
    CityMissing,
    NoData,
    Partial,
    Unavailable,
}
