package nvk.cotrip.backend.http

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
