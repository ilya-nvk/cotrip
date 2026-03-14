package nvk.cotrip.ui.forecast

import android.content.Context
import nvk.cotrip.R
import nvk.cotrip.data.network.dto.WeatherForecastDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TripForecastUiMapper {
    fun mapDays(context: Context, response: WeatherForecastResponseDto): List<ForecastDayUi> {
        return response.items
            .sortedBy { it.date }
            .map { it.toUi(context) }
    }

    fun source(context: Context, response: WeatherForecastResponseDto): String {
        return response.items.firstOrNull()?.source
            ?: context.getString(R.string.trip_forecast_source_default)
    }

    fun lastUpdated(response: WeatherForecastResponseDto): String {
        return response.items.maxByOrNull { it.fetchedAt }
            ?.fetchedAt
            ?.let(::formatUpdatedAt)
            .orEmpty()
    }

    fun coverageMessage(
        context: Context,
        hasSelectedCity: Boolean,
        response: WeatherForecastResponseDto,
    ): String? {
        if (!hasSelectedCity) {
            return context.getString(R.string.trip_forecast_coverage_city_missing)
        }
        if (response.missingDates.isEmpty()) return null
        if (response.items.isEmpty()) {
            return context.getString(R.string.trip_forecast_coverage_unavailable)
        }
        val availableTo = response.availableTo?.let { date ->
            runCatching {
                LocalDate.parse(date).format(
                    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                )
            }.getOrDefault(date)
        }
        return if (availableTo != null) {
            context.getString(R.string.trip_forecast_coverage_available_through, availableTo)
        } else {
            context.getString(R.string.trip_forecast_coverage_partial)
        }
    }

    private fun WeatherForecastDto.toUi(context: Context): ForecastDayUi {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        val today = LocalDate.now()
        val title = when (parsedDate) {
            null -> date
            today -> context.getString(R.string.trip_forecast_day_today)
            today.plusDays(1) -> context.getString(R.string.trip_forecast_day_tomorrow)
            else -> parsedDate.format(
                DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            )
        }
        val subtitle = parsedDate
            ?.takeIf { it == today || it == today.plusDays(1) }
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
            else -> context.getString(R.string.common_empty_placeholder)
        }

        return ForecastDayUi(
            title = title,
            subtitle = subtitle,
            icon = icon,
            iconTint = tint,
            temp = tempText,
            description = description?.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            } ?: context.getString(R.string.trip_forecast_description_missing),
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
