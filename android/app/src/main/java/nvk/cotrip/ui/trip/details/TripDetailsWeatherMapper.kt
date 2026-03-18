package nvk.cotrip.ui.trip.details

import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.WeatherCloudy
import nvk.cotrip.ui.theme.WeatherRainy
import nvk.cotrip.ui.theme.WeatherSunny
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TripDetailsWeatherMapper {
    fun pickCity(days: List<ItineraryDayDto>): String? {
        val sorted = days.sortedBy { it.dayNumber }
        val withCoordinates = sorted
            .firstOrNull { !it.city.isNullOrBlank() && it.cityLat != null && it.cityLon != null }
            ?.city
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (withCoordinates != null) return withCoordinates

        return sorted
            .firstOrNull { !it.city.isNullOrBlank() }
            ?.city
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun cityMissingCard(isCitySelectable: Boolean = false): WeatherCardUi {
        return WeatherCardUi(
            city = "",
            days = emptyList(),
            notice = WeatherCardNotice.CityMissing,
            isCitySelectable = isCitySelectable,
        )
    }

    fun unavailableCard(city: String, isCitySelectable: Boolean = false): WeatherCardUi {
        return WeatherCardUi(
            city = city,
            days = emptyList(),
            notice = WeatherCardNotice.Unavailable,
            isCitySelectable = isCitySelectable,
        )
    }

    fun mapResponse(
        city: String,
        response: WeatherForecastResponseDto,
        isCitySelectable: Boolean = false,
    ): WeatherCardUi {
        val days = response.items
            .sortedBy { it.date }
            .take(5)
            .map { forecast ->
                val dayDate = runCatching { LocalDate.parse(forecast.date) }.getOrNull()
                WeatherDayUi(
                    label = dayDate?.format(DateTimeFormatter.ofPattern("EEE", appUiLocale()))
                        ?: "—",
                    temp = buildTempText(forecast.tempMin, forecast.tempMax),
                    icon = when {
                        forecast.iconCode?.startsWith("01") == true -> CoTripIcons.WeatherSunny
                        forecast.iconCode?.startsWith("09") == true || forecast.iconCode?.startsWith("10") == true -> CoTripIcons.WeatherRain
                        else -> CoTripIcons.WeatherCloudy
                    },
                    tint = when {
                        forecast.iconCode?.startsWith("01") == true -> WeatherSunny
                        forecast.iconCode?.startsWith("09") == true || forecast.iconCode?.startsWith(
                            "10"
                        ) == true -> WeatherRainy

                        else -> WeatherCloudy
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
            isCitySelectable = isCitySelectable,
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
