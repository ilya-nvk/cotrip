package nvk.cotrip.data.repository

import nvk.cotrip.data.network.dto.WeatherForecastResponseDto

interface WeatherRepository {
    suspend fun getWeather(
        tripId: String,
        city: String,
        start: String? = null,
        end: String? = null,
    ): WeatherForecastResponseDto

    suspend fun refreshWeather(
        tripId: String,
        city: String,
        start: String? = null,
        end: String? = null,
    ): WeatherForecastResponseDto
}
