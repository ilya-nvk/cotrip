package nvk.cotrip.data.network.dto

fun ItineraryDayDto.cityDisplayLabel(): String {
    cityDisplayName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val raw = city?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
    return raw.substringBefore(',').trim().ifBlank { raw }
}
