package nvk.cotrip.backend.integrations

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class OpenWeatherCityCandidate(
    val name: String,
    val fullText: String,
    val lat: Double,
    val lon: Double,
    val providerId: String,
)

data class OpenWeatherDailyForecast(
    val date: LocalDate,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val iconCode: String?,
)

object OpenWeatherClient {
    internal var httpClientForTest: HttpClient? = null
    private val defaultClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    private val httpClient: HttpClient get() = httpClientForTest ?: defaultClient

    suspend fun searchCities(
        apiKey: String,
        query: String,
        limit: Int,
        preferredLang: String? = null,
    ): List<OpenWeatherCityCandidate> {
        val response = httpClient.get("https://api.openweathermap.org/geo/1.0/direct") {
            parameter("q", query)
            parameter("limit", limit.coerceIn(1, 20))
            parameter("appid", apiKey)
        }.body<List<DirectGeocodingDto>>()

        return response
            .mapNotNull { item ->
                val cityName = localizedDirectCityName(item, preferredLang)
                if (cityName.isBlank()) return@mapNotNull null
                val parts = listOfNotNull(
                    cityName.takeIf { it.isNotBlank() },
                    item.state?.trim()?.takeIf { it.isNotBlank() },
                    item.country?.trim()?.takeIf { it.isNotBlank() },
                )
                val providerId = "owm:${item.lat}:${item.lon}"
                OpenWeatherCityCandidate(
                    name = cityName,
                    fullText = parts.joinToString(", "),
                    lat = item.lat,
                    lon = item.lon,
                    providerId = providerId,
                )
            }
            .distinctBy { candidate -> candidate.providerId }
    }

    private fun localizedDirectCityName(item: DirectGeocodingDto, preferredLang: String?): String {
        val lang = preferredLang?.lowercase()?.take(2)?.takeIf { it.isNotBlank() }
        val fromLocal = lang?.let { l -> item.localNames?.get(l) }
        return (fromLocal ?: item.name).trim()
    }

    suspend fun reverseLocalCityLabel(
        apiKey: String,
        lat: Double,
        lon: Double,
        preferredLang: String,
    ): String? {
        val lang = preferredLang.lowercase().take(2).ifBlank { return null }
        val response = httpClient.get("https://api.openweathermap.org/geo/1.0/reverse") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("limit", 1)
            parameter("appid", apiKey)
        }.body<List<ReverseGeocodingDto>>()
        val item = response.firstOrNull() ?: return null
        val fromLocal = item.localNames?.get(lang)
            ?: item.localNames?.entries?.firstOrNull()?.value
        return (fromLocal ?: item.name).trim().takeIf { it.isNotEmpty() }
    }

    suspend fun fetchDailyForecast(
        apiKey: String,
        lat: Double,
        lon: Double,
    ): List<OpenWeatherDailyForecast> {
        val response = httpClient.get("https://api.openweathermap.org/data/3.0/onecall") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("units", "metric")
            parameter("exclude", "minutely,hourly,alerts,current")
            parameter("appid", apiKey)
        }.body<OneCallResponseDto>()

        return response.daily.orEmpty().map { day ->
            val date = Instant.ofEpochSecond(day.dt + response.timezoneOffset)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate()
            OpenWeatherDailyForecast(
                date = date,
                tempMin = day.temp?.min,
                tempMax = day.temp?.max,
                description = day.weather?.firstOrNull()?.description,
                iconCode = day.weather?.firstOrNull()?.icon,
            )
        }
    }
}

@Serializable
private data class DirectGeocodingDto(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String? = null,
    val state: String? = null,
    @SerialName("local_names") val localNames: Map<String, String>? = null,
)

@Serializable
private data class ReverseGeocodingDto(
    val name: String,
    @SerialName("local_names") val localNames: Map<String, String>? = null,
)

@Serializable
private data class OneCallResponseDto(
    @SerialName("timezone_offset")
    val timezoneOffset: Long = 0L,
    val daily: List<OneCallDailyDto>? = null,
)

@Serializable
private data class OneCallDailyDto(
    val dt: Long,
    val temp: OneCallTempDto? = null,
    val weather: List<OneCallWeatherDto>? = null,
)

@Serializable
private data class OneCallTempDto(
    val min: Double? = null,
    val max: Double? = null,
)

@Serializable
private data class OneCallWeatherDto(
    val description: String? = null,
    val icon: String? = null,
)
