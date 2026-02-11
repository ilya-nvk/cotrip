package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto

interface WeatherCacheStore {
    fun observeWeather(key: String): Flow<WeatherForecastResponseDto?>
    suspend fun getWeather(key: String): WeatherForecastResponseDto?
    suspend fun setWeather(key: String, response: WeatherForecastResponseDto)
    suspend fun clear()
}
