package nvk.cotrip.data.network.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

sealed interface CommentWsEvent {
    data class CommentCreated(val payload: CommentCreatedPayload) : CommentWsEvent
    data class CommentDeleted(val payload: CommentDeletedPayload) : CommentWsEvent
    data class Closed(val code: Int, val reason: String) : CommentWsEvent
    data class Error(val cause: Throwable) : CommentWsEvent
}

class CommentsWebSocket(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val _events = MutableSharedFlow<CommentWsEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private val connected = AtomicBoolean(false)

    fun connect(baseUrl: String, tripId: String, token: String) {
        if (connected.getAndSet(true)) return
        val wsUrl = buildWsUrl(baseUrl, tripId, token)
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = runCatching { json.decodeFromString<WsEnvelope>(text).type }.getOrNull()
                when (type) {
                    "comment.created" -> {
                        val message = runCatching { json.decodeFromString<CommentCreatedMessage>(text) }
                            .getOrNull()
                        message?.let { _events.tryEmit(CommentWsEvent.CommentCreated(it.payload)) }
                    }
                    "comment.deleted" -> {
                        val message = runCatching { json.decodeFromString<CommentDeletedMessage>(text) }
                            .getOrNull()
                        message?.let { _events.tryEmit(CommentWsEvent.CommentDeleted(it.payload)) }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                this@CommentsWebSocket.webSocket = null
                _events.tryEmit(CommentWsEvent.Error(t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                this@CommentsWebSocket.webSocket = null
                _events.tryEmit(CommentWsEvent.Closed(code, reason))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        })
    }

    fun sendCreate(ideaId: String, body: String, clientMessageId: String? = null): Boolean {
        val socket = webSocket ?: return false
        val message = CommentCreateMessage(
            payload = CommentCreatePayload(
                ideaId = ideaId,
                body = body,
                clientMessageId = clientMessageId
            )
        )
        return socket.send(json.encodeToString(message))
    }

    fun disconnect() {
        connected.set(false)
        webSocket?.close(1000, "client_closed")
        webSocket = null
    }

    private fun buildWsUrl(baseUrl: String, tripId: String, token: String): String {
        val trimmed = baseUrl.trimEnd('/')
        val scheme = if (trimmed.startsWith("https://")) "wss://" else "ws://"
        val host = trimmed.removePrefix("https://").removePrefix("http://")
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString())
        return "$scheme$host/v1/ws/trips/$tripId/comments?token=$encodedToken"
    }
}

@Serializable
private data class WsEnvelope(
    val type: String,
)

@Serializable
data class CommentCreateMessage(
    val type: String = "comment.create",
    val payload: CommentCreatePayload,
)

@Serializable
data class CommentCreatedMessage(
    val type: String = "comment.created",
    val payload: CommentCreatedPayload,
)

@Serializable
data class CommentDeletedMessage(
    val type: String = "comment.deleted",
    val payload: CommentDeletedPayload,
)

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
    val type: String = "user",
    val body: String,
    val createdAt: String,
    val clientMessageId: String? = null,
)

@Serializable
data class CommentDeletedPayload(
    val id: String,
    val ideaId: String,
)
