package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeatherForecastDto(
    val id: String,
    val tripId: String,
    val city: String,
    val date: String,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val description: String? = null,
    val iconCode: String? = null,
    val source: String,
    val fetchedAt: String,
)

@Serializable
data class WeatherForecastResponseDto(
    val items: List<WeatherForecastDto> = emptyList(),
    val nextCursor: String? = null,
    val cacheUsed: Boolean = false,
    val availableFrom: String? = null,
    val availableTo: String? = null,
    val missingDates: List<String> = emptyList(),
    val nextRefreshAt: String? = null,
    val displayCity: String? = null,
)
