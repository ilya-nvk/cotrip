package nvk.cotrip.data.network

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitReachedTest {
    @Test
    fun given_validDetailsJson_when_parseLimitReachedDetails_then_returnsParsedObject() {
        // GIVEN
        val details = buildJsonObject {
            put("entity", "trip")
            put("scopeId", "user-1")
            put("limit", 5)
            put("currentCount", 5)
            put("oldestCandidate", buildJsonObject {
                put("id", "trip-1")
                put("label", "Old trip")
                put("startDate", "2026-01-10")
                put("deletable", true)
            })
        }

        // WHEN
        val parsed = parseLimitReachedDetails(details)

        // THEN
        assertNotNull(parsed)
        val value = requireNotNull(parsed)
        assertEquals("trip", value.entity)
        assertEquals("user-1", value.scopeId)
        assertEquals(5, value.limit)
        assertEquals(5, value.currentCount)
        assertEquals("trip-1", value.oldestCandidate?.id)
        assertTrue(value.oldestCandidate?.deletable == true)
    }

    @Test
    fun given_detailsWithMissingMandatoryFields_when_parseLimitReachedDetails_then_returnsNull() {
        // GIVEN
        val details = buildJsonObject {
            put("entity", "trip")
            put("limit", 5)
        }

        // WHEN
        val parsed = parseLimitReachedDetails(details)

        // THEN
        assertNull(parsed)
    }

    @Test
    fun given_failureWithNonLimitReachedCode_when_limitReachedDetails_then_returnsNull() {
        // GIVEN
        val failure = ApiResult.Failure(
            error = ApiError(code = "invalid_payload", message = "x"),
        )

        // WHEN
        val details = failure.limitReachedDetails()

        // THEN
        assertNull(details)
    }
}
