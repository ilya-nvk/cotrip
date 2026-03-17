package nvk.cotrip.backend.routes.v1

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.backend.testing.PostgresIntegrationTest
import nvk.cotrip.backend.testing.TestApplicationSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@PostgresIntegrationTest
class SyncRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun given_tripAndActivityChanges_when_postSyncChanges_then_bothAppliedInOneBatch() = withApp { session ->
        // GIVEN
        val token = session.accessToken
        val tripId = randomUuid()
        val day1 = randomUuid()
        val day2 = randomUuid()
        val activityId = randomUuid()

        // WHEN
        val response = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                """
                {
                  "items": [
                    {
                      "changeId": "c-trip",
                      "entity": "trip",
                      "id": "$tripId",
                      "type": "create",
                      "payload": {
                        "title": "Offline Trip",
                        "description": "from test",
                        "startDate": "2026-06-10",
                        "endDate": "2026-06-11",
                        "currencyCode": "EUR",
                        "days": [
                          {"id": "$day1", "date": "2026-06-10", "dayNumber": 1},
                          {"id": "$day2", "date": "2026-06-11", "dayNumber": 2}
                        ]
                      }
                    },
                    {
                      "changeId": "c-activity",
                      "entity": "activity",
                      "id": "$activityId",
                      "type": "create",
                      "payload": {
                        "dayId": "$day1",
                        "title": "Museum"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        // THEN
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val applied = body["applied"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        val conflicts = body["conflicts"]!!.jsonArray
        assertEquals(setOf("c-trip", "c-activity"), applied)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun given_createWithoutChangeId_when_postSyncChanges_then_appliedUsesEntityId() = withApp { session ->
        // GIVEN
        val token = session.accessToken
        val tripId = randomUuid()
        val day1 = randomUuid()
        val day2 = randomUuid()

        // WHEN
        val response = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                """
                {
                  "items": [
                    {
                      "entity": "trip",
                      "id": "$tripId",
                      "type": "create",
                      "payload": {
                        "title": "Compat Trip",
                        "startDate": "2026-06-10",
                        "endDate": "2026-06-11",
                        "currencyCode": "EUR",
                        "days": [
                          {"id": "$day1", "date": "2026-06-10", "dayNumber": 1},
                          {"id": "$day2", "date": "2026-06-11", "dayNumber": 2}
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        // THEN
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val applied = body["applied"]!!.jsonArray.map { it.jsonPrimitive.content }
        val conflicts = body["conflicts"]!!.jsonArray
        assertEquals(listOf(tripId), applied)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun given_activityWithMissingDay_when_postSyncChanges_then_retryableConflictReturned() = withApp { session ->
        // GIVEN
        val token = session.accessToken

        // WHEN
        val response = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                """
                {
                  "items": [
                    {
                      "changeId": "c-missing-day",
                      "entity": "activity",
                      "id": "${randomUuid()}",
                      "type": "create",
                      "payload": {
                        "dayId": "${randomUuid()}",
                        "title": "Should Retry"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        // THEN
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val conflicts = body["conflicts"]!!.jsonArray
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first().jsonObject
        assertEquals("c-missing-day", conflict["changeId"]!!.jsonPrimitive.content)
        assertEquals("dependency_not_ready", conflict["reason"]!!.jsonPrimitive.content)
        assertTrue(conflict["retryable"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun given_duplicateCreate_when_postSyncChangesTwice_then_bothAppliedIdempotent() = withApp { session ->
        // GIVEN
        val token = session.accessToken
        val tripId = randomUuid()
        val day1 = randomUuid()
        val day2 = randomUuid()
        val payload = """
            {
              "title": "Idempotent Trip",
              "startDate": "2026-07-01",
              "endDate": "2026-07-02",
              "currencyCode": "EUR",
              "days": [
                {"id": "$day1", "date": "2026-07-01", "dayNumber": 1},
                {"id": "$day2", "date": "2026-07-02", "dayNumber": 2}
              ]
            }
        """.trimIndent()

        // WHEN
        repeat(2) { index ->
            val response = client.post("/v1/sync/changes") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    """
                    {
                      "items": [
                        {
                          "changeId": "dup-$index",
                          "entity": "trip",
                          "id": "$tripId",
                          "type": "create",
                          "payload": $payload
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }
            // THEN (each call)
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.body<String>()).jsonObject
            val applied = body["applied"]!!.jsonArray.map { it.jsonPrimitive.content }
            val conflicts = body["conflicts"]!!.jsonArray
            assertEquals(listOf("dup-$index"), applied)
            assertTrue(conflicts.isEmpty())
        }
    }

    @Test
    fun given_validTripAndInvalidExpense_when_postSyncChanges_then_appliedAndConflictReturned() = withApp { session ->
        // GIVEN
        val token = session.accessToken
        val tripId = randomUuid()
        val day1 = randomUuid()
        val day2 = randomUuid()

        // WHEN
        val response = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                """
                {
                  "items": [
                    {
                      "changeId": "ok-trip",
                      "entity": "trip",
                      "id": "$tripId",
                      "type": "create",
                      "payload": {
                        "title": "Mixed Batch Trip",
                        "startDate": "2026-08-01",
                        "endDate": "2026-08-02",
                        "currencyCode": "EUR",
                        "days": [
                          {"id": "$day1", "date": "2026-08-01", "dayNumber": 1},
                          {"id": "$day2", "date": "2026-08-02", "dayNumber": 2}
                        ]
                      }
                    },
                    {
                      "changeId": "bad-expense",
                      "entity": "expense",
                      "id": "${randomUuid()}",
                      "type": "create",
                      "payload": {
                        "tripId": "$tripId",
                        "title": "Broken expense",
                        "amount": 10.0,
                        "status": "paid",
                        "splitType": "invalid",
                        "participants": []
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        // THEN
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val applied = body["applied"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        val conflicts = body["conflicts"]!!.jsonArray
        assertTrue("ok-trip" in applied)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first().jsonObject
        assertEquals("bad-expense", conflict["changeId"]!!.jsonPrimitive.content)
        assertEquals("invalid_payload", conflict["reason"]!!.jsonPrimitive.content)
        assertTrue(!conflict["retryable"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun given_invalidJsonBody_when_postSyncChanges_then_badRequest() = withApp { session ->
        // GIVEN / WHEN
        val response = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            setBody("invalid json body")
        }
        // THEN
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun withApp(
        block: suspend io.ktor.server.testing.ApplicationTestBuilder.(TestApplicationSupport.DevSession) -> Unit,
    ) {
        TestApplicationSupport.withApp(block)
    }

    private fun randomUuid(): String = java.util.UUID.randomUUID().toString()
}
