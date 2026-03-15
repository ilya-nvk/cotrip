package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto

interface WeatherRepository {
    suspend fun getCachedWeather(
        tripId: String,
        city: String,
        start: String? = null,
        end: String? = null,
    ): WeatherForecastResponseDto?

    fun getWeather(
        tripId: String,
        city: String,
        start: String? = null,
        end: String? = null,
    ): Flow<WeatherForecastResponseDto>

    suspend fun refreshWeather(
        tripId: String,
        city: String,
        start: String? = null,
        end: String? = null,
    ): Result<Unit>

    suspend fun fetchWeatherSnapshot(
        tripId: String,
        city: String,
        start: String? = null,
        end: String? = null,
    ): Result<WeatherForecastResponseDto>
}
