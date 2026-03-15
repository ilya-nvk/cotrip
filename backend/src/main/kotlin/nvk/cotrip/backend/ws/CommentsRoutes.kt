package nvk.cotrip.backend.ws

import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import nvk.cotrip.backend.auth.JwtService
import nvk.cotrip.backend.db.CommentRepository
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.UserRepository
import nvk.cotrip.backend.notifications.NotificationService

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
            val ideaId = create.payload.ideaId
            val body = create.payload.body.trim()
            val clientMessageId = create.payload.clientMessageId
            if (body.isBlank()) return

            val ideaTripId = IdeaRepository.findTripIdByIdeaId(ideaId) ?: return
            if (ideaTripId != tripId) return
            if (!TripRepository.isMember(tripId, userId)) return

            val stored = CommentRepository.create(ideaId, userId, body)
            val actorName = UserRepository.findById(userId)?.name ?: "Someone"
            publishCommentCreated(
                tripId = tripId,
                comment = stored,
                authorName = actorName,
                clientMessageId = clientMessageId,
            )
            NotificationService.notifyIdeaComment(
                tripId = tripId,
                ideaId = stored.ideaId,
                actorUserId = userId,
                actorName = actorName,
                body = stored.body
            )
        }
        else -> Unit
    }
}

@Serializable
private data class WsEnvelope(val type: String)
