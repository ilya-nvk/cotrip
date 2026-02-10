package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto

class WeatherRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
) : WeatherRepository {
    override suspend fun getWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): WeatherForecastResponseDto {
        return api.getWeather(tripId = tripId, city = city, start = start, end = end)
    }

    override suspend fun refreshWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): WeatherForecastResponseDto {
        return api.refreshWeather(tripId = tripId, city = city, start = start, end = end)
    }
}
