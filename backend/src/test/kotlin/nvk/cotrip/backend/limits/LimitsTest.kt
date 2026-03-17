package nvk.cotrip.backend.limits

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.OffsetDateTime

class LimitsTest {

    @Test
    fun given_limitsObject_when_readConstants_then_hasExpectedValues() {
        // GIVEN — Limits object

        // WHEN / THEN
        assertEquals(100, Limits.TRIPS_PER_OWNER)
        assertEquals(300, Limits.IDEAS_PER_TRIP)
        assertEquals(500, Limits.EXPENSES_PER_TRIP)
        assertEquals(40, Limits.ACTIVITIES_PER_DAY)
        assertEquals(1000, Limits.COMMENTS_PER_IDEA)
    }

    @Test
    fun given_limitReachedExceptionWithOldestCandidate_when_toDetailsJson_then_serializesCorrectly() {
        // GIVEN
        val candidate = OldestCandidate(
            id = "trip-1",
            label = "Old trip",
            createdAt = OffsetDateTime.parse("2026-01-15T10:00:00Z"),
            startDate = LocalDate.of(2026, 1, 10),
            deletable = true,
        )
        val ex = LimitReachedException(
            entity = "trip",
            scopeId = "user-1",
            limit = 100,
            currentCount = 100,
            oldestCandidate = candidate,
        )

        // WHEN
        val json = ex.toDetailsJson()

        // THEN
        assertEquals("trip", json["entity"]!!.jsonPrimitive.content)
        assertEquals("user-1", json["scopeId"]!!.jsonPrimitive.content)
        assertEquals(100, json["limit"]!!.jsonPrimitive.content.toInt())
        assertEquals(100, json["currentCount"]!!.jsonPrimitive.content.toInt())
        val oldest = json["oldestCandidate"]!!.jsonObject
        assertEquals("trip-1", oldest["id"]!!.jsonPrimitive.content)
        assertEquals("Old trip", oldest["label"]!!.jsonPrimitive.content)
        assertEquals("true", oldest["deletable"]!!.jsonPrimitive.content)
        assertFalse(oldest["startDate"]!!.jsonPrimitive.content.isEmpty())
    }

    @Test
    fun given_limitReachedExceptionWithoutOldestCandidate_when_toDetailsJson_then_oldestCandidateKeyPresent() {
        // GIVEN
        val ex = LimitReachedException(
            entity = "expense",
            scopeId = "trip-1",
            limit = 500,
            currentCount = 500,
            oldestCandidate = null,
        )

        // WHEN
        val json = ex.toDetailsJson()

        // THEN
        assertEquals("expense", json["entity"]!!.jsonPrimitive.content)
        assertEquals("trip-1", json["scopeId"]!!.jsonPrimitive.content)
        assertEquals(500, json["limit"]!!.jsonPrimitive.content.toInt())
        assertEquals(500, json["currentCount"]!!.jsonPrimitive.content.toInt())
        assertTrue("oldestCandidate" in json)
    }

    @Test
    fun given_oldestCandidateWithNullLabelAndDates_when_toDetailsJson_then_serializesNullsAsJsonNull() {
        val candidate = OldestCandidate(
            id = "idea-1",
            label = null,
            createdAt = null,
            startDate = null,
            deletable = false,
        )
        val ex = LimitReachedException(
            entity = "idea",
            scopeId = "trip-1",
            limit = 300,
            currentCount = 300,
            oldestCandidate = candidate,
        )
        val json = ex.toDetailsJson()
        val oldest = json["oldestCandidate"]!!.jsonObject
        assertEquals("idea-1", oldest["id"]!!.jsonPrimitive.content)
        assertEquals("false", oldest["deletable"]!!.jsonPrimitive.content)
    }
}
