package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

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
data class CreateTripRequest(
    val title: String,
    val description: String? = null,
    val startDate: String,
    val endDate: String,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String,
)

@Serializable
data class UpdateTripRequest(
    val title: String? = null,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String? = null,
)

@Serializable
data class TransferOwnerRequest(
    val newOwnerId: String,
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
data class MemberDto(
    val userId: String,
    val name: String,
    val photoUrl: String? = null,
    val initials: String,
    val role: String,
    val status: String,
)
