package nvk.cotrip.backend.ws

import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.send
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.parseToJsonElement
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
        json.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.content
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

private suspend fun broadcast(tripId: String, payload: String) {
    val sessions = CommentsHub.sessions(tripId)
    sessions.forEach { session ->
        runCatching { session.send(Frame.Text(payload)) }
    }
}
