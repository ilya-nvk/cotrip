package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ActivityDto(
    val id: String,
    val dayId: String,
    val title: String,
    val timeText: String? = null,
    val locationName: String? = null,
    val locationLink: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val orderIndex: Int,
)

@Serializable
data class ItineraryDayDto(
    val id: String,
    val tripId: String,
    val date: String,
    val dayNumber: Int,
    val city: String? = null,
    val isOutOfRange: Boolean,
    val activities: List<ActivityDto> = emptyList(),
)

@Serializable
data class TrimOutOfRangeRequest(
    val action: String,
    val dayIds: List<String>,
)
