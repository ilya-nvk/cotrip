package nvk.cotrip.ui.activity.form

private const val PLACE_ID_PREFIX = "place_id:"

fun String.toGoogleMapsPlaceLink(): String {
    return "https://www.google.com/maps/search/?api=1&query_place_id=$this&query=$PLACE_ID_PREFIX$this"
}

fun extractGooglePlaceId(link: String?): String? {
    if (link.isNullOrBlank()) return null
    return when {
        "query_place_id=" in link -> {
            link.substringAfter("query_place_id=").substringBefore('&').ifBlank { null }
        }
        PLACE_ID_PREFIX in link -> {
            link.substringAfter(PLACE_ID_PREFIX).substringBefore('&').ifBlank { null }
        }
        else -> null
    }
}
