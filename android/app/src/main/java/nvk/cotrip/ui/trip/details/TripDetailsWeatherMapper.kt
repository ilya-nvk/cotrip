package nvk.cotrip.ui.trip.details

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning

object TripDetailsWeatherMapper {
    fun pickCity(days: List<ItineraryDayDto>): String? {
        return days
            .sortedBy { it.dayNumber }
            .firstOrNull { !it.city.isNullOrBlank() && it.cityLat != null && it.cityLon != null }
            ?.city
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun cityMissingCard(): WeatherCardUi {
        return WeatherCardUi(
            city = "",
            days = emptyList(),
            notice = WeatherCardNotice.CityMissing,
        )
    }

    fun unavailableCard(city: String): WeatherCardUi {
        return WeatherCardUi(
            city = city,
            days = emptyList(),
            notice = WeatherCardNotice.Unavailable,
        )
    }

    fun mapResponse(city: String, response: WeatherForecastResponseDto): WeatherCardUi {
        val days = response.items
            .sortedBy { it.date }
            .take(5)
            .map { forecast ->
                val dayDate = runCatching { LocalDate.parse(forecast.date) }.getOrNull()
                WeatherDayUi(
                    label = dayDate?.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
                        ?: "—",
                    temp = buildTempText(forecast.tempMin, forecast.tempMax),
                    icon = when {
                        forecast.iconCode?.startsWith("01") == true -> CoTripIcons.WeatherSunny
                        forecast.iconCode?.startsWith("09") == true || forecast.iconCode?.startsWith("10") == true -> CoTripIcons.WeatherRain
                        else -> CoTripIcons.WeatherCloudy
                    },
                    tint = when {
                        forecast.iconCode?.startsWith("01") == true -> Warning
                        forecast.iconCode?.startsWith("09") == true || forecast.iconCode?.startsWith("10") == true -> Info
                        else -> TextSecondary
                    },
                )
            }

        val notice = when {
            days.isEmpty() -> WeatherCardNotice.NoData
            response.missingDates.isNotEmpty() -> WeatherCardNotice.Partial
            else -> WeatherCardNotice.None
        }

        return WeatherCardUi(
            city = city,
            days = days,
            notice = notice,
        )
    }

    private fun buildTempText(tempMin: Double?, tempMax: Double?): String {
        return when {
            tempMin != null && tempMax != null -> "${tempMin.roundTemp()}°/${tempMax.roundTemp()}°"
            tempMax != null -> "${tempMax.roundTemp()}°"
            tempMin != null -> "${tempMin.roundTemp()}°"
            else -> "—"
        }
    }

    private fun Double.roundTemp(): Int = kotlin.math.round(this).toInt()
}
