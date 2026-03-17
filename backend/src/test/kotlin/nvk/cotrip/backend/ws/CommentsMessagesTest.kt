package nvk.cotrip.backend.ws

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsMessagesTest {

    private val json = WsJson.instance

    @Test
    fun given_commentCreatedMessage_when_encodeAndDecode_then_roundTripsCorrectly() {
        // GIVEN
        val payload = CommentCreatedPayload(
            id = "comment-1",
            ideaId = "idea-1",
            authorId = "user-1",
            authorName = "Alice",
            type = "text",
            body = "Hello",
            createdAt = "2026-03-17T12:00:00Z",
            clientMessageId = "client-1",
        )
        val message = CommentCreatedMessage(payload = payload)

        // WHEN
        val encoded = json.encodeToString(CommentCreatedMessage.serializer(), message)
        val decoded = json.decodeFromString(CommentCreatedMessage.serializer(), encoded)

        // THEN
        assertEquals("comment.created", decoded.type)
        assertEquals("comment-1", decoded.payload.id)
        assertEquals("idea-1", decoded.payload.ideaId)
        assertEquals("Hello", decoded.payload.body)
        assertEquals("client-1", decoded.payload.clientMessageId)
    }

    @Test
    fun given_commentCreatePayload_when_encodeAndDecode_then_roundTripsCorrectly() {
        // GIVEN
        val payload = CommentCreatePayload(
            ideaId = "idea-1",
            body = "Test body",
            clientMessageId = "client-1",
        )

        // WHEN
        val encoded = json.encodeToString(CommentCreatePayload.serializer(), payload)
        val decoded = json.decodeFromString(CommentCreatePayload.serializer(), encoded)

        // THEN
        assertEquals("idea-1", decoded.ideaId)
        assertEquals("Test body", decoded.body)
        assertEquals("client-1", decoded.clientMessageId)
    }

    @Test
    fun given_commentDeletedMessage_when_encodeAndDecode_then_roundTripsCorrectly() {
        val payload = CommentDeletedPayload(id = "c1", ideaId = "i1")
        val message = CommentDeletedMessage(payload = payload)
        val encoded = json.encodeToString(CommentDeletedMessage.serializer(), message)
        val decoded = json.decodeFromString(CommentDeletedMessage.serializer(), encoded)
        assertEquals("comment.deleted", decoded.type)
        assertEquals("c1", decoded.payload.id)
        assertEquals("i1", decoded.payload.ideaId)
    }

    @Test
    fun given_commentRejectedMessage_when_encodeAndDecode_then_roundTripsCorrectly() {
        val payload = CommentRejectedPayload(clientMessageId = "client-1", reason = "spam", details = null)
        val message = CommentRejectedMessage(payload = payload)
        val encoded = json.encodeToString(CommentRejectedMessage.serializer(), message)
        val decoded = json.decodeFromString(CommentRejectedMessage.serializer(), encoded)
        assertEquals("comment.rejected", decoded.type)
        assertEquals("spam", decoded.payload.reason)
    }
}
