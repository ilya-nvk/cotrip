package nvk.cotrip.backend.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import nvk.cotrip.backend.comments.SystemCommentMetadataResolver
import nvk.cotrip.backend.db.CommentRow

suspend fun publishCommentCreated(
    tripId: String,
    comment: CommentRow,
    authorName: String? = null,
    clientMessageId: String? = null,
) {
    val meta = SystemCommentMetadataResolver.resolve(comment.type, comment.body)
    val payload = CommentCreatedMessage(
        payload = CommentCreatedPayload(
            id = comment.id,
            ideaId = comment.ideaId,
            authorId = comment.authorId,
            authorName = authorName,
            type = comment.type,
            body = comment.body,
            createdAt = comment.createdAt.toString(),
            clientMessageId = clientMessageId,
            systemKey = meta.systemKey,
            systemActorName = meta.systemActorName,
        )
    )
    val text = WsJson.instance.encodeToString(payload)
    CommentsHub.sessions(tripId).forEach { session ->
        runCatching { session.send(Frame.Text(text)) }
    }
}
