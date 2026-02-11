package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nvk.cotrip.data.cache.WeatherCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.util.AppLogger
import java.io.IOException
import javax.inject.Inject

class WeatherRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val weatherCacheStore: WeatherCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : WeatherRepository {

    private companion object {
        private const val TAG = "WeatherRepository"
    }

    override suspend fun getWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Flow<WeatherForecastResponseDto> {
        val cacheKey = cacheKey(tripId = tripId, city = city, start = start, end = end)
        if (networkStateProvider.isOnline()) {
            runCatching {
                api.getWeather(tripId = tripId, city = city, start = start, end = end)
            }.onSuccess { response ->
                safeLocalMutation("getWeather.setWeather(key=$cacheKey)") {
                    weatherCacheStore.setWeather(cacheKey, response)
                }
            }.onFailure { error ->
                AppLogger.w(TAG, "getWeather network fetch failed key=$cacheKey", error)
            }
        }
        return weatherCacheStore.observeWeather(cacheKey).map { cached ->
            cached ?: throw IOException("Weather $cacheKey is not available in cache")
        }
    }

    override suspend fun refreshWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Result<Unit> {
        if (!networkStateProvider.isOnline()) {
            return Result.failure(IOException("Weather refresh requires network"))
        }
        val cacheKey = cacheKey(tripId = tripId, city = city, start = start, end = end)
        return runCatching {
            val response =
                api.refreshWeather(tripId = tripId, city = city, start = start, end = end)
            safeLocalMutation("refreshWeather.setWeather(key=$cacheKey)") {
                weatherCacheStore.setWeather(cacheKey, response)
            }
        }
    }

    private fun cacheKey(tripId: String, city: String, start: String?, end: String?): String {
        val normalizedCity = city.trim().lowercase()
        val normalizedStart = start.orEmpty()
        val normalizedEnd = end.orEmpty()
        return "$tripId|$normalizedCity|$normalizedStart|$normalizedEnd"
    }
}
