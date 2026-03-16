package nvk.cotrip.backend.routes.v1

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.backend.module
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun syncCreateTripAndActivity_areAppliedInOneBatch() = withApp { token ->
        val tripId = randomUuid()
        val day1 = randomUuid()
        val day2 = randomUuid()
        val activityId = randomUuid()

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

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val applied = body["applied"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        val conflicts = body["conflicts"]!!.jsonArray
        assertEquals(setOf("c-trip", "c-activity"), applied)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun syncCreateWithoutChangeId_appliedFallsBackToEntityId() = withApp { token ->
        val tripId = randomUuid()
        val day1 = randomUuid()
        val day2 = randomUuid()

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

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        val applied = body["applied"]!!.jsonArray.map { it.jsonPrimitive.content }
        val conflicts = body["conflicts"]!!.jsonArray
        assertEquals(listOf(tripId), applied)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun syncCreateDependencyMissing_returnsRetryableConflict() = withApp { token ->
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
    fun syncDuplicateCreate_isIdempotentAndApplied() = withApp { token ->
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
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.body<String>()).jsonObject
            val applied = body["applied"]!!.jsonArray.map { it.jsonPrimitive.content }
            val conflicts = body["conflicts"]!!.jsonArray
            assertEquals(listOf("dup-$index"), applied)
            assertTrue(conflicts.isEmpty())
        }
    }

    @Test
    fun syncMixedBatch_returnsAppliedAndConflictTogether() = withApp { token ->
        val tripId = randomUuid()
        val day1 = randomUuid()
        val day2 = randomUuid()

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

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(String) -> Unit) {
        val dbUrl = System.getenv("DATABASE_URL")
        val dbUser = System.getenv("DATABASE_USER")
        val dbPassword = System.getenv("DATABASE_PASSWORD") ?: ""
        assumeTrue(
            !dbUrl.isNullOrBlank() && !dbUser.isNullOrBlank(),
            "Set DATABASE_URL and DATABASE_USER to run SyncRoutesTest"
        )
        assumeTrue(
            canConnect(dbUrl = dbUrl, dbUser = dbUser, dbPassword = dbPassword),
            "Postgres is not reachable for DATABASE_URL"
        )

        testApplication {
            environment {
                config = MapApplicationConfig(
                    "ktor.db.url" to dbUrl,
                    "ktor.db.user" to dbUser,
                    "ktor.db.password" to dbPassword,
                    "ktor.devAuthEnabled" to "true",
                )
            }
            application {
                module()
            }

            val tokenResponse = client.post("/v1/auth/dev") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "googleId": "sync-test-${System.currentTimeMillis()}",
                      "name": "Sync Test"
                    }
                    """.trimIndent()
                )
            }
            assertEquals(HttpStatusCode.OK, tokenResponse.status)
            val tokenBody = json.parseToJsonElement(tokenResponse.body<String>()).jsonObject
            val accessToken = tokenBody["accessToken"]!!.jsonPrimitive.content
            block(accessToken)
        }
    }

    private fun canConnect(dbUrl: String, dbUser: String, dbPassword: String): Boolean {
        return runCatching {
            DriverManager.setLoginTimeout(2)
            DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { connection ->
                connection.prepareStatement("SELECT 1").use { statement ->
                    statement.executeQuery().use { result ->
                        result.next()
                    }
                }
            }
        }.isSuccess
    }

    private fun randomUuid(): String = java.util.UUID.randomUUID().toString()
}
