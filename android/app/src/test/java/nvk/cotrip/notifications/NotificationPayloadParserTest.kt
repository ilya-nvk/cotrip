package nvk.cotrip.notifications

import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.NotificationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationPayloadParserTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val parser = NotificationPayloadParser(json)

    @Test
    fun given_validPayload_when_parse_then_returnsNotificationDto() {
        val data = mapOf(
            "notificationId" to "notif-1",
            "type" to "idea_comment",
            "payload" to """{"ideaId":"idea-1","tripId":"trip-1"}""",
            "createdAt" to "2026-01-15T12:00:00Z",
        )
        val result = parser.parse(data, defaultCreatedAt = "2026-01-01T00:00:00Z")
        assertNotNull(result)
        assertEquals("notif-1", result!!.id)
        assertEquals("idea_comment", result.type)
        assertEquals("2026-01-15T12:00:00Z", result.createdAt)
        assertNotNull(result.payload)
    }

    @Test
    fun given_emptyId_when_parse_then_returnsNull() {
        val data = mapOf(
            "notificationId" to "  ",
            "type" to "idea_comment",
            "payload" to "{}",
        )
        assertNull(parser.parse(data, defaultCreatedAt = "2026-01-01T00:00:00Z"))
    }

    @Test
    fun given_emptyType_when_parse_then_returnsNull() {
        val data = mapOf(
            "notificationId" to "notif-1",
            "type" to "",
            "payload" to "{}",
        )
        assertNull(parser.parse(data, defaultCreatedAt = "2026-01-01T00:00:00Z"))
    }

    @Test
    fun given_missingType_when_parse_then_returnsNull() {
        val data = mapOf(
            "notificationId" to "notif-1",
            "payload" to "{}",
        )
        assertNull(parser.parse(data, defaultCreatedAt = "2026-01-01T00:00:00Z"))
    }

    @Test
    fun given_invalidJsonPayload_when_parse_then_returnsNull() {
        val data = mapOf(
            "notificationId" to "notif-1",
            "type" to "idea_comment",
            "payload" to "{invalid json",
        )
        assertNull(parser.parse(data, defaultCreatedAt = "2026-01-01T00:00:00Z"))
    }

    @Test
    fun given_emptyPayloadKey_when_parse_then_usesEmptyObject() {
        val data = mapOf(
            "notificationId" to "notif-1",
            "type" to "expense_created",
        )
        val result = parser.parse(data, defaultCreatedAt = "2026-01-01T00:00:00Z")
        assertNotNull(result)
        assertEquals("notif-1", result!!.id)
        assertEquals("{}", result.payload.toString())
    }

    @Test
    fun given_noCreatedAt_when_parse_then_usesDefaultCreatedAt() {
        val data = mapOf(
            "notificationId" to "notif-1",
            "type" to "idea_comment",
            "payload" to "{}",
        )
        val result = parser.parse(data, defaultCreatedAt = "2026-06-15T00:00:00Z")
        assertNotNull(result)
        assertEquals("2026-06-15T00:00:00Z", result!!.createdAt)
    }
}
