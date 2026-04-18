package nvk.cotrip.backend.http

/**
 * Maps the first [Accept-Language] tag to a two-letter code used with OpenWeather `local_names`.
 * Only [en] and [ru] are handled for UI; other locales fall back to API default `name` fields.
 */
fun preferredOpenWeatherUiLang(acceptLanguage: String?): String? {
    val header = acceptLanguage?.trim()?.lowercase() ?: return null
    if (header.isEmpty()) return null
    val firstPart = header.split(',').firstOrNull()?.trim() ?: return null
    val tag = firstPart.substringBefore(';').trim().ifEmpty { return null }
    val primary = tag.substringBefore('-').take(2).lowercase()
    return when (primary) {
        "en", "ru" -> primary
        else -> null
    }
}
