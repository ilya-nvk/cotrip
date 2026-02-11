package nvk.cotrip.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import javax.inject.Inject

class WeatherCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : WeatherCacheStore {

    override fun observeWeather(key: String): Flow<WeatherForecastResponseDto?> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[WEATHER_KEY]).byKey[key]
        }
    }

    override suspend fun getWeather(key: String): WeatherForecastResponseDto? {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[WEATHER_KEY]).byKey[key]
    }

    override suspend fun setWeather(key: String, response: WeatherForecastResponseDto) {
        updateCache { cache ->
            cache.copy(byKey = cache.byKey.toMutableMap().apply { put(key, response) })
        }
    }

    override suspend fun clear() {
        updateCache { cache -> cache.copy(byKey = emptyMap()) }
    }

    private suspend fun updateCache(transform: (WeatherCache) -> WeatherCache) {
        dataStore.edit { prefs ->
            val current = decodeCache(prefs[WEATHER_KEY])
            val updated = transform(current)
            prefs[WEATHER_KEY] = json.encodeToString(WeatherCache.serializer(), updated)
        }
    }

    private fun decodeCache(raw: String?): WeatherCache {
        if (raw.isNullOrBlank()) return WeatherCache()
        return runCatching { json.decodeFromString(WeatherCache.serializer(), raw) }
            .getOrElse { WeatherCache() }
    }

    @Serializable
    private data class WeatherCache(
        val byKey: Map<String, WeatherForecastResponseDto> = emptyMap(),
    )

    private companion object {
        private val WEATHER_KEY = stringPreferencesKey("weather_cache")
    }
}
