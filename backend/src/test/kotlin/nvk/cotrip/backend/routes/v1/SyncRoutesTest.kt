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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.backend.testing.DbFixtureSupport.createIdea
import nvk.cotrip.backend.testing.DbFixtureSupport.createTrip
import nvk.cotrip.backend.testing.DbFixtureSupport.id
import nvk.cotrip.backend.testing.DbFixtureSupport.joinTrip
import nvk.cotrip.backend.testing.DbFixtureSupport.listItineraryDays
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

    @Test
    fun given_newCommandEntities_when_postSyncChanges_then_happyPathApplies() = withApp { owner ->
        // GIVEN
        val trip = createTrip(accessToken = owner.accessToken, title = "Sync Commands Trip")
        val tripId = trip.id()
        val dayId = listItineraryDays(owner.accessToken, tripId).first().jsonObject["id"]!!.jsonPrimitive.content
        val member = createDevSession(googleId = "sync-member-${randomUuid()}", name = "Sync Member")
        joinTrip(accessToken = member.accessToken, tripId = tripId)
        val memberIdea = createIdea(accessToken = member.accessToken, tripId = tripId, title = "Member Idea")
        val ideaId = memberIdea.id()

        val activityCreateResponse = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            setBody(
                """
                {
                  "items": [
                    {
                      "changeId": "create-activity-1",
                      "entity": "activity",
                      "id": "${randomUuid()}",
                      "type": "create",
                      "payload": {"dayId":"$dayId","title":"A1"}
                    },
                    {
                      "changeId": "create-activity-2",
                      "entity": "activity",
                      "id": "${randomUuid()}",
                      "type": "create",
                      "payload": {"dayId":"$dayId","title":"A2"}
                    }
                  ]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, activityCreateResponse.status)

        val aiResponse = client.post("/v1/trips/$tripId/ai/suggestions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            setBody("""{"city":"Paris","typeOptions":["Museum"]}""")
        }
        assertEquals(HttpStatusCode.OK, aiResponse.status)
        val aiSuggestionId = json.parseToJsonElement(aiResponse.body<String>())
            .jsonObject["items"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content

        // WHEN
        val response = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            setBody(
                """
                {
                  "items": [
                    {
                      "changeId": "trip-member-delete",
                      "entity": "trip_member",
                      "id": "$tripId:${member.userId}",
                      "type": "delete",
                      "payload": {"tripId":"$tripId","memberId":"${member.userId}"}
                    },
                    {
                      "changeId": "idea-status",
                      "entity": "idea_status",
                      "id": "$ideaId",
                      "type": "upsert",
                      "payload": {"status":"approved"}
                    },
                    {
                      "changeId": "idea-convert",
                      "entity": "idea_convert",
                      "id": "$ideaId",
                      "type": "create",
                      "payload": {"dayId":"$dayId"}
                    },
                    {
                      "changeId": "activity-reorder",
                      "entity": "activity_reorder",
                      "id": "$dayId",
                      "type": "upsert",
                      "payload": {"dayId":"$dayId","orderedIds":[]}
                    },
                    {
                      "changeId": "itinerary-trim",
                      "entity": "itinerary_trim",
                      "id": "$tripId",
                      "type": "upsert",
                      "payload": {"tripId":"$tripId","action":"keep","dayIds":[]}
                    },
                    {
                      "changeId": "notification-settings",
                      "entity": "notification_settings",
                      "id": "me",
                      "type": "upsert",
                      "payload": {"items":[{"key":"expenses_new","enabled":false}]}
                    },
                    {
                      "changeId": "notification-read",
                      "entity": "notification_read",
                      "id": "non_comment",
                      "type": "upsert",
                      "payload": {"mode":"non_comment"}
                    },
                    {
                      "changeId": "user-profile",
                      "entity": "user_profile",
                      "id": "me",
                      "type": "upsert",
                      "payload": {"name":"Owner Renamed"}
                    },
                    {
                      "changeId": "ai-save",
                      "entity": "ai_suggestion_save",
                      "id": "$aiSuggestionId",
                      "type": "upsert",
                      "payload": {"suggestionId":"$aiSuggestionId"}
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
        assertTrue(conflicts.isEmpty())
        assertEquals(
            setOf(
                "trip-member-delete",
                "idea-status",
                "idea-convert",
                "activity-reorder",
                "itinerary-trim",
                "notification-settings",
                "notification-read",
                "user-profile",
                "ai-save",
            ),
            applied
        )
    }

    @Test
    fun given_newCommandsWithForbiddenAndInvalidPayload_when_postSyncChanges_then_conflictsContainReasons() = withApp { owner ->
        // GIVEN
        val trip = createTrip(accessToken = owner.accessToken, title = "Sync Commands Failures")
        val tripId = trip.id()
        val dayId = listItineraryDays(owner.accessToken, tripId).first().jsonObject["id"]!!.jsonPrimitive.content
        val member = createDevSession(googleId = "sync-member-forbidden-${randomUuid()}", name = "Forbidden Member")
        joinTrip(accessToken = member.accessToken, tripId = tripId)

        // WHEN
        val response = client.post("/v1/sync/changes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
            setBody(
                """
                {
                  "items": [
                    {
                      "changeId": "forbidden-member-delete-owner",
                      "entity": "trip_member",
                      "id": "$tripId:${owner.userId}",
                      "type": "delete",
                      "payload": {"tripId":"$tripId","memberId":"${owner.userId}"}
                    },
                    {
                      "changeId": "invalid-idea-status",
                      "entity": "idea_status",
                      "id": "${randomUuid()}",
                      "type": "upsert",
                      "payload": {"status":"broken"}
                    },
                    {
                      "changeId": "invalid-trim-action",
                      "entity": "itinerary_trim",
                      "id": "$tripId",
                      "type": "upsert",
                      "payload": {"tripId":"$tripId","action":"bad","dayIds":[]}
                    },
                    {
                      "changeId": "invalid-notification-read",
                      "entity": "notification_read",
                      "id": "x",
                      "type": "upsert",
                      "payload": {"mode":"idea_comments"}
                    },
                    {
                      "changeId": "invalid-user-profile",
                      "entity": "user_profile",
                      "id": "me",
                      "type": "upsert",
                      "payload": {"name":"  "}
                    },
                    {
                      "changeId": "invalid-activity-reorder",
                      "entity": "activity_reorder",
                      "id": "$dayId",
                      "type": "upsert",
                      "payload": {"dayId":"$dayId","orderedIds":["not-a-uuid"]}
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        // THEN
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val conflicts = body["conflicts"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, conflicts.size)
        val reasons = conflicts.associate {
            it["changeId"]!!.jsonPrimitive.content to it["reason"]!!.jsonPrimitive.content
        }
        assertEquals("forbidden", reasons["forbidden-member-delete-owner"])
        assertEquals("invalid_payload", reasons["invalid-idea-status"])
        assertEquals("invalid_payload", reasons["invalid-trim-action"])
        assertEquals("invalid_payload", reasons["invalid-notification-read"])
        assertEquals("invalid_payload", reasons["invalid-user-profile"])
        assertEquals("invalid_payload", reasons["invalid-activity-reorder"])
    }

    @Test
    fun given_idempotentCommandOps_when_postSyncChangesTwice_then_secondCallHasNoConflicts() = withApp { owner ->
        // GIVEN
        val trip = createTrip(accessToken = owner.accessToken, title = "Sync Commands Idempotent")
        val tripId = trip.id()
        val idea = createIdea(accessToken = owner.accessToken, tripId = tripId, title = "Idempotent Idea")
        val ideaId = idea.id()

        val payload = """
            {
              "items": [
                {
                  "changeId": "idempotent-idea-status",
                  "entity": "idea_status",
                  "id": "$ideaId",
                  "type": "upsert",
                  "payload": {"status":"approved"}
                },
                {
                  "changeId": "idempotent-notification-settings",
                  "entity": "notification_settings",
                  "id": "me",
                  "type": "upsert",
                  "payload": {"items":[{"key":"expenses_new","enabled":true}]}
                },
                {
                  "changeId": "idempotent-user-profile",
                  "entity": "user_profile",
                  "id": "me",
                  "type": "upsert",
                  "payload": {"name":"Owner Stable"}
                }
              ]
            }
        """.trimIndent()

        // WHEN
        repeat(2) { index ->
            val response = client.post("/v1/sync/changes") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
                setBody(payload.replace("idempotent-", "idempotent-$index-"))
            }

            // THEN
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.body<String>()).jsonObject
            val conflicts = body["conflicts"]!!.jsonArray
            assertTrue(conflicts.isEmpty())
        }
    }

    private fun withApp(
        block: suspend io.ktor.server.testing.ApplicationTestBuilder.(TestApplicationSupport.DevSession) -> Unit,
    ) {
        TestApplicationSupport.withApp(block)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createDevSession(
        googleId: String,
        name: String,
    ): TestApplicationSupport.DevSession {
        val response = client.post("/v1/auth/dev") {
            contentType(ContentType.Application.Json)
            setBody("""{"googleId":"$googleId","name":"$name"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        return TestApplicationSupport.DevSession(
            accessToken = body["accessToken"]!!.jsonPrimitive.content,
            refreshToken = body["refreshToken"]!!.jsonPrimitive.content,
            userId = body["user"]!!.jsonObject["id"]!!.jsonPrimitive.content,
        )
    }

    private fun randomUuid(): String = java.util.UUID.randomUUID().toString()
}
