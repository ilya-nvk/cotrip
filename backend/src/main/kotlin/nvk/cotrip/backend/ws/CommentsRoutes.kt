package nvk.cotrip.backend.ws

import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nvk.cotrip.backend.auth.JwtService
import java.time.Instant
import java.util.UUID

fun Route.commentsWebSocket() {
    webSocket("/v1/ws/trips/{tripId}/comments") {
        val tripId = call.parameters["tripId"]
        if (tripId.isNullOrBlank()) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing tripId"))
            return@webSocket
        }

        val token = call.request.queryParameters["token"]
        val userId = JwtService.userIdFromToken(token)
        if (userId.isNullOrBlank()) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return@webSocket
        }

        CommentsHub.add(tripId, this)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    handleTextFrame(tripId, userId, frame.readText())
                }
            }
        } finally {
            CommentsHub.remove(tripId, this)
        }
    }
}

private suspend fun handleTextFrame(tripId: String, userId: String, text: String) {
    val json = WsJson.instance
    val type = runCatching {
        json.decodeFromString<WsEnvelope>(text).type
    }.getOrNull()

    when (type) {
        "comment.create" -> {
            val create = json.decodeFromString<CommentCreateMessage>(text)
            val created = CommentCreatedMessage(
                payload = CommentCreatedPayload(
                    id = UUID.randomUUID().toString(),
                    ideaId = create.payload.ideaId,
                    authorId = userId,
                    body = create.payload.body,
                    createdAt = Instant.now().toString(),
                )
            )
            broadcast(tripId, json.encodeToString(created))
        }
        else -> Unit
    }
}

@Serializable
private data class WsEnvelope(val type: String)

private suspend fun broadcast(tripId: String, payload: String) {
    val sessions = CommentsHub.sessions(tripId)
    sessions.forEach { session ->
        runCatching { session.send(Frame.Text(payload)) }
    }
}
