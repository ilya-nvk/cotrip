package nvk.cotrip.ui.forecast

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import nvk.cotrip.data.network.dto.WeatherForecastDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning

object TripForecastUiMapper {
    fun mapDays(response: WeatherForecastResponseDto): List<ForecastDayUi> {
        return response.items
            .sortedBy { it.date }
            .map { it.toUi() }
    }

    fun source(response: WeatherForecastResponseDto): String {
        return response.items.firstOrNull()?.source ?: "OpenWeather"
    }

    fun lastUpdated(response: WeatherForecastResponseDto): String {
        return response.items.maxByOrNull { it.fetchedAt }
            ?.fetchedAt
            ?.let(::formatUpdatedAt)
            .orEmpty()
    }

    fun coverageMessage(hasSelectedCity: Boolean, response: WeatherForecastResponseDto): String? {
        if (!hasSelectedCity) {
            return "Select a city in itinerary first to get weather."
        }
        if (response.missingDates.isEmpty()) return null
        if (response.items.isEmpty()) {
            return "Forecast is not available for these dates yet. OpenWeather provides up to 8 upcoming days."
        }
        val availableTo = response.availableTo?.let { date ->
            runCatching {
                LocalDate.parse(date).format(
                    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                )
            }.getOrDefault(date)
        }
        return if (availableTo != null) {
            "Forecast is available through $availableTo. Remaining dates will appear later."
        } else {
            "Forecast is partially available. Remaining dates will appear later."
        }
    }

    private fun WeatherForecastDto.toUi(): ForecastDayUi {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        val today = LocalDate.now()
        val title = when (parsedDate) {
            null -> date
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> parsedDate.format(
                DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            )
        }
        val subtitle = parsedDate
            ?.takeUnless { it == today || it == today.plusDays(1) }
            ?.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        val icon = when {
            iconCode?.startsWith("01") == true -> CoTripIcons.WeatherSunny
            iconCode?.startsWith("09") == true || iconCode?.startsWith("10") == true -> CoTripIcons.WeatherRain
            else -> CoTripIcons.WeatherCloudy
        }
        val tint = when (icon) {
            CoTripIcons.WeatherSunny -> Warning
            CoTripIcons.WeatherRain -> Info
            else -> TextSecondary
        }
        val tempText = when {
            tempMin != null && tempMax != null -> "${tempMin.roundTemp()}° / ${tempMax.roundTemp()}°"
            tempMax != null -> "${tempMax.roundTemp()}°"
            tempMin != null -> "${tempMin.roundTemp()}°"
            else -> "—"
        }

        return ForecastDayUi(
            title = title,
            subtitle = subtitle,
            icon = icon,
            iconTint = tint,
            temp = tempText,
            description = description?.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            } ?: "No description",
        )
    }

    private fun formatUpdatedAt(raw: String): String {
        return runCatching {
            val parsed = OffsetDateTime.parse(raw)
            parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault()))
        }.getOrDefault(raw)
    }

    private fun Double.roundTemp(): Int = kotlin.math.round(this).toInt()
}
