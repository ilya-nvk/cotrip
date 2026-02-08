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
