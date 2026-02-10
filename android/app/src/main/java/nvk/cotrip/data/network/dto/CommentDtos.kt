package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CommentDto(
    val id: String,
    val ideaId: String,
    val authorId: String,
    val type: String = "user",
    val body: String,
    val createdAt: String,
)
