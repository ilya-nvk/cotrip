package nvk.cotrip.backend.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface WsMessage {
    val type: String
}

@Serializable
@SerialName("comment.create")
data class CommentCreateMessage(
    override val type: String = "comment.create",
    val payload: CommentCreatePayload,
) : WsMessage

@Serializable
@SerialName("comment.created")
data class CommentCreatedMessage(
    override val type: String = "comment.created",
    val payload: CommentCreatedPayload,
) : WsMessage

@Serializable
@SerialName("comment.deleted")
data class CommentDeletedMessage(
    override val type: String = "comment.deleted",
    val payload: CommentDeletedPayload,
) : WsMessage

@Serializable
data class CommentCreatePayload(
    val ideaId: String,
    val body: String,
)

@Serializable
data class CommentCreatedPayload(
    val id: String,
    val ideaId: String,
    val authorId: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class CommentDeletedPayload(
    val id: String,
    val ideaId: String,
)
