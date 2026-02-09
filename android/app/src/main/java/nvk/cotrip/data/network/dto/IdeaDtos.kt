package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateIdeaRequest(
    val title: String,
    val city: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateIdeaRequest(
    val title: String? = null,
    val city: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
)

@Serializable
data class ConvertIdeaRequest(
    val dayId: String,
    val timeText: String? = null,
    val orderIndex: Int? = null,
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
    val commentsCount: Int = 0,
)
