package nvk.cotrip.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
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

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getCachedWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): WeatherForecastResponseDto? {
        val cacheKey = cacheKey(tripId = tripId, city = city, start = start, end = end)
        return weatherCacheStore.getWeather(cacheKey)
    }

    override fun getWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Flow<WeatherForecastResponseDto> {
        val cacheKey = cacheKey(tripId = tripId, city = city, start = start, end = end)
        if (networkStateProvider.isOnline()) {
            ioScope.launch {
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
        }
        return weatherCacheStore.observeWeather(cacheKey).mapNotNull { it }
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

    override suspend fun fetchWeatherSnapshot(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Result<WeatherForecastResponseDto> {
        val cacheKey = cacheKey(tripId = tripId, city = city, start = start, end = end)
        return runCatching {
            val response = api.getWeather(tripId = tripId, city = city, start = start, end = end)
            safeLocalMutation("fetchWeatherSnapshot.setWeather(key=$cacheKey)") {
                weatherCacheStore.setWeather(cacheKey, response)
            }
            response
        }
    }

    private fun cacheKey(tripId: String, city: String, start: String?, end: String?): String {
        val normalizedCity = city.trim().lowercase()
        val normalizedStart = start.orEmpty()
        val normalizedEnd = end.orEmpty()
        return "$tripId|$normalizedCity|$normalizedStart|$normalizedEnd"
    }
}
