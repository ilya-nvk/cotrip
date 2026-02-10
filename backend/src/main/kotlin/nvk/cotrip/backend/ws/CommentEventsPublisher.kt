package nvk.cotrip.backend.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import nvk.cotrip.backend.db.CommentRow

suspend fun publishCommentCreated(tripId: String, comment: CommentRow) {
    val payload = CommentCreatedMessage(
        payload = CommentCreatedPayload(
            id = comment.id,
            ideaId = comment.ideaId,
            authorId = comment.authorId,
            type = comment.type,
            body = comment.body,
            createdAt = comment.createdAt.toString(),
        )
    )
    val text = WsJson.instance.encodeToString(payload)
    CommentsHub.sessions(tripId).forEach { session ->
        runCatching { session.send(Frame.Text(text)) }
    }
}
