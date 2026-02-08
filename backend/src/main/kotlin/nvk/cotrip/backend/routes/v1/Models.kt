package nvk.cotrip.backend.routes.v1

import kotlinx.serialization.Serializable
import nvk.cotrip.backend.db.TripRow
import nvk.cotrip.backend.db.UserRow
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val initials: String,
)

@Serializable
data class TripDto(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String? = null,
    val startDate: String,
    val endDate: String,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String,
    val status: String,
    val updatedAt: String,
)

@Serializable
data class InviteLinkDto(
    val token: String,
    val url: String,
    val expiresAt: String,
)

@Serializable
data class InviteInfoDto(
    val tripId: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val locationLine: String? = null,
    val expiresAt: String,
)

@Serializable
data class CommentDto(
    val id: String,
    val ideaId: String,
    val authorId: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class MemberDto(
    val userId: String,
    val name: String,
    val photoUrl: String? = null,
    val initials: String,
    val role: String,
    val status: String,
)

@Serializable
data class IdeaDto(
    val id: String,
    val tripId: String,
    val authorId: String,
    val title: String,
    val city: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val status: String,
    val updatedAt: String,
)

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

fun UserRow.toDto(): UserDto = UserDto(
    id = id,
    name = name,
    photoUrl = photoUrl,
    initials = initialsFromName(name),
)

fun TripRow.toDto(): TripDto = TripDto(
    id = id,
    ownerId = ownerId,
    title = title,
    description = description,
    startDate = startDate.format(dateFormatter),
    endDate = endDate.format(dateFormatter),
    locationLine = locationLine,
    coverUrl = coverUrl,
    currencyCode = currencyCode,
    status = status,
    updatedAt = updatedAt.toString(),
)

fun initialsFromName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> ("${parts[0].first()}${parts[1].first()}").uppercase()
    }
}
