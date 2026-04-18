package nvk.cotrip.backend.http

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nvk.cotrip.backend.integrations.OpenWeatherClient

suspend fun reverseGeocodeDisplayCity(
    apiKey: String?,
    lat: Double,
    lon: Double,
    acceptLanguage: String?,
): String? {
    val uiLang = preferredOpenWeatherUiLang(acceptLanguage) ?: return null
    if (apiKey.isNullOrBlank()) return null
    return runCatching {
        OpenWeatherClient.reverseLocalCityLabel(apiKey, lat, lon, uiLang)
    }.getOrNull()
}

suspend fun batchReverseGeocodeDisplayCities(
    coordinates: List<Pair<Double, Double>>,
    apiKey: String?,
    acceptLanguage: String?,
): Map<Pair<Double, Double>, String?> {
    val uiLang = preferredOpenWeatherUiLang(acceptLanguage) ?: return emptyMap()
    if (apiKey.isNullOrBlank()) return emptyMap()
    val unique = coordinates.distinct()
    if (unique.isEmpty()) return emptyMap()
    return coroutineScope {
        unique.map { coord ->
            async {
                coord to runCatching {
                    OpenWeatherClient.reverseLocalCityLabel(apiKey, coord.first, coord.second, uiLang)
                }.getOrNull()
            }
        }.awaitAll().toMap()
    }
}
