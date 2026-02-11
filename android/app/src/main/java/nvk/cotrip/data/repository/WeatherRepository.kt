package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto

interface WeatherRepository {
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
}
