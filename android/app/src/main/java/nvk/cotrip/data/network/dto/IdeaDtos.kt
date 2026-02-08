package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

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
