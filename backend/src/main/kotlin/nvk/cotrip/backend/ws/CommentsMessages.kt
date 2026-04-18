package nvk.cotrip.backend.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
@SerialName("comment.rejected")
data class CommentRejectedMessage(
    override val type: String = "comment.rejected",
    val payload: CommentRejectedPayload,
) : WsMessage

@Serializable
data class CommentCreatePayload(
    val ideaId: String,
    val body: String,
    val clientMessageId: String? = null,
)

@Serializable
data class CommentCreatedPayload(
    val id: String,
    val ideaId: String,
    val authorId: String,
    val authorName: String? = null,
    val type: String,
    val body: String,
    val createdAt: String,
    val clientMessageId: String? = null,
    val systemKey: String? = null,
    val systemActorName: String? = null,
)

@Serializable
data class CommentDeletedPayload(
    val id: String,
    val ideaId: String,
)

@Serializable
data class CommentRejectedPayload(
    val clientMessageId: String? = null,
    val reason: String,
    val details: JsonObject? = null,
)
