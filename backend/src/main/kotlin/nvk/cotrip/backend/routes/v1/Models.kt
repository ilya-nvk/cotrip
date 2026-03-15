package nvk.cotrip.backend.routes.v1

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    val authorName: String? = null,
    val type: String,
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
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
    val status: String,
    val updatedAt: String,
    val commentsCount: Int = 0,
)

@Serializable
data class ActivityDto(
    val id: String,
    val dayId: String,
    val sourceIdeaId: String? = null,
    val title: String,
    val timeText: String? = null,
    val locationName: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
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
    val cityProviderId: String? = null,
    val cityLat: Double? = null,
    val cityLon: Double? = null,
    val isOutOfRange: Boolean,
    val activities: List<ActivityDto> = emptyList(),
)

@Serializable
data class CitySuggestionDto(
    val name: String,
    val providerId: String? = null,
    val lat: Double,
    val lon: Double,
    val fullText: String,
)

@Serializable
data class PlaceSuggestionDto(
    val name: String,
    val placeId: String,
    val fullText: String,
)

@Serializable
data class ExpenseParticipantDto(
    val userId: String,
    val shareAmount: Double? = null,
    val isIncluded: Boolean,
    val isPaid: Boolean,
    val name: String? = null,
)

@Serializable
data class ExpenseDto(
    val id: String,
    val tripId: String,
    val title: String,
    val amount: Double,
    val currencyCode: String,
    val status: String,
    val paidById: String? = null,
    val date: String? = null,
    val splitType: String,
    val note: String? = null,
    val participants: List<ExpenseParticipantDto> = emptyList(),
)

@Serializable
data class WeatherForecastDto(
    val id: String,
    val tripId: String,
    val city: String,
    val date: String,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val description: String? = null,
    val iconCode: String? = null,
    val source: String,
    val fetchedAt: String,
)

@Serializable
data class WeatherForecastResponseDto(
    val items: List<WeatherForecastDto> = emptyList(),
    val nextCursor: String? = null,
    val cacheUsed: Boolean = false,
    val availableFrom: String? = null,
    val availableTo: String? = null,
    val missingDates: List<String> = emptyList(),
    val nextRefreshAt: String? = null,
)

@Serializable
data class AiSuggestionDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val typeLabel: String? = null,
    val durationLabel: String? = null,
    val budgetLabel: String? = null,
    val estimatedCost: Double? = null,
    val isSaved: Boolean,
)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val payload: JsonElement,
    val createdAt: String,
    val readAt: String? = null,
)

@Serializable
data class NotificationSettingDto(
    val key: String,
    val enabled: Boolean,
)

@Serializable
data class SyncChangeDto(
    val entity: String,
    val id: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val payload: JsonElement,
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
