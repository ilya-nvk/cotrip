package nvk.cotrip.data.network.ws

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsWebSocketTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun given_connected_when_receiveCommentCreated_then_emitsCommentCreatedEvent() = runTest {
        val (client, listenerSlot) = createClientCapturingListener()
        val ws = CommentsWebSocket(client, json)
        val events = mutableListOf<CommentWsEvent>()
        launch { ws.events.take(1).toList().let { events.addAll(it) } }
        advanceUntilIdle() // let collector subscribe
        ws.connect("https://api.example/", "trip-1", "token")
        val listener = listenerSlot.captured
        val message = """{"type":"comment.created","payload":{"id":"c1","ideaId":"i1","authorId":"a1","authorName":"Alice","type":"user","body":"hello","createdAt":"2026-01-01T00:00:00Z","clientMessageId":null}}"""
        listener.onMessage(mockWs(), message)
        advanceUntilIdle()

        assertTrue(events.size == 1 && events[0] is CommentWsEvent.CommentCreated)
        val event = events[0] as CommentWsEvent.CommentCreated
        assertEquals("c1", event.payload.id)
        assertEquals("hello", event.payload.body)
    }

    @Test
    fun given_connected_when_receiveCommentDeleted_then_emitsCommentDeletedEvent() = runTest {
        val (client, listenerSlot) = createClientCapturingListener()
        val ws = CommentsWebSocket(client, json)
        val events = mutableListOf<CommentWsEvent>()
        launch { ws.events.take(1).toList().let { events.addAll(it) } }
        advanceUntilIdle()
        ws.connect("https://api.example/", "trip-1", "token")
        val listener = listenerSlot.captured
        val message = """{"type":"comment.deleted","payload":{"id":"c1","ideaId":"i1"}}"""
        listener.onMessage(mockWs(), message)
        advanceUntilIdle()

        assertTrue(events.size == 1 && events[0] is CommentWsEvent.CommentDeleted)
        assertEquals("c1", (events[0] as CommentWsEvent.CommentDeleted).payload.id)
    }

    @Test
    fun given_connected_when_receiveInvalidJson_then_emitsNothing() = runTest {
        val (client, listenerSlot) = createClientCapturingListener()
        val ws = CommentsWebSocket(client, json)
        ws.connect("https://api.example/", "trip-1", "token")
        val listener = listenerSlot.captured

        listener.onMessage(mockWs(), "{invalid")

        // Invalid JSON does not emit; then send valid message and assert we get it
        val events = mutableListOf<CommentWsEvent>()
        launch { ws.events.take(1).toList().let { events.addAll(it) } }
        advanceUntilIdle()
        val message = """{"type":"comment.created","payload":{"id":"c1","ideaId":"i1","authorId":"a1","body":"hi","createdAt":"2026-01-01T00:00:00Z"}}"""
        listener.onMessage(mockWs(), message)
        advanceUntilIdle()
        assertTrue(events.size == 1 && events[0] is CommentWsEvent.CommentCreated)
    }

    @Test
    fun given_connected_when_sendCreate_then_returnsTrue() = runTest {
        val mockSocket = mockk<WebSocket>(relaxed = true)
        every { mockSocket.send(any<String>()) } returns true
        val (client2, _) = createClientCapturingListener(webSocket = mockSocket)
        val ws = CommentsWebSocket(client2, json)
        ws.connect("https://api.example/", "trip-1", "token")

        val sent = ws.sendCreate("idea-1", "body text", "client-msg-1")
        assertTrue(sent)
    }

    @Test
    fun given_disconnected_when_sendCreate_then_returnsFalse() = runTest {
        val client = mockk<OkHttpClient>(relaxed = true)
        every { client.newWebSocket(any(), any()) } returns mockk(relaxed = true)
        val ws = CommentsWebSocket(client, json)
        ws.connect("https://api.example/", "trip-1", "token")
        ws.disconnect()

        val sent = ws.sendCreate("idea-1", "body", null)
        assertFalse(sent)
    }

    @Test
    fun given_httpsBaseUrl_when_connect_then_doesNotThrow() {
        val (client, _) = createClientCapturingListener()
        val ws = CommentsWebSocket(client, json)
        ws.connect("https://api.example.com/", "trip-1", "token+special")
        ws.disconnect()
    }

    private fun mockWs(): WebSocket = mockk(relaxed = true)

    private fun createClientCapturingListener(webSocket: WebSocket? = null): Pair<OkHttpClient, CapturingSlot<WebSocketListener>> {
        val listenerSlot = slot<WebSocketListener>()
        val mockWs = webSocket ?: mockk(relaxed = true)
        val client = mockk<OkHttpClient>(relaxed = true)
        every { client.newWebSocket(any(), capture(listenerSlot)) } returns mockWs
        return client to listenerSlot
    }
}
