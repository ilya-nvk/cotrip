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
data class UpdateDayRequest(
    val city: String? = null,
)

@Serializable
data class CreateActivityRequest(
    val title: String,
    val timeText: String? = null,
    val locationName: String? = null,
    val locationLink: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val orderIndex: Int? = null,
)

@Serializable
data class UpdateActivityRequest(
    val title: String? = null,
    val timeText: String? = null,
    val locationName: String? = null,
    val locationLink: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
)

@Serializable
data class MoveActivityRequest(
    val dayId: String,
    val orderIndex: Int? = null,
)

@Serializable
data class ReorderActivitiesRequest(
    val orderedIds: List<String>,
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
