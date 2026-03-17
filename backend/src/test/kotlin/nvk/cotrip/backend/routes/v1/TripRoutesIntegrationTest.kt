package nvk.cotrip.backend.routes.v1

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.backend.testing.PostgresIntegrationTest
import nvk.cotrip.backend.testing.TestApplicationSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@PostgresIntegrationTest
class TripRoutesIntegrationTest {
    private val json = TestApplicationSupport.json

    @Test
    fun given_invalidTripDates_when_postTrips_then_badRequestWithInvalidDatesCode() = TestApplicationSupport.withApp { session ->
        // GIVEN
        val accessToken = session.accessToken

        // WHEN
        val response = client.post("/v1/trips") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(
                """
                {
                  "title": "Broken trip",
                  "startDate": "2026-06-20",
                  "endDate": "2026-06-10",
                  "currencyCode": "EUR"
                }
                """.trimIndent()
            )
        }

        // THEN
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        assertEquals("invalid_dates", body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun given_session_when_createPatchArchiveDeleteTrip_then_roundTripSucceeds() = TestApplicationSupport.withApp { session ->
        // GIVEN
        val accessToken = session.accessToken

        // WHEN
        val created = createTrip(
            accessToken = accessToken,
            title = "Summer test trip",
            startDate = "2026-07-10",
            endDate = "2026-07-12",
        )
        val tripId = created["id"]!!.jsonPrimitive.content

        val listed = client.get("/v1/trips?limit=100") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val updated = client.patch("/v1/trips/$tripId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody("""{"title":"Updated trip title"}""")
        }
        val archive = client.post("/v1/trips/$tripId/archive") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val delete = client.delete("/v1/trips/$tripId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        // THEN
        assertEquals("Summer test trip", created["title"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, listed.status)
        val listedBody = json.parseToJsonElement(listed.body<String>()).jsonObject
        val items = listedBody["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]!!.jsonPrimitive.content == tripId })
        assertEquals(HttpStatusCode.OK, updated.status)
        val updatedBody = json.parseToJsonElement(updated.body<String>()).jsonObject
        assertEquals("Updated trip title", updatedBody["title"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.NoContent, archive.status)
        assertEquals(HttpStatusCode.NoContent, delete.status)
    }

    @Test
    fun given_nonexistentTripId_when_getTrip_then_notFound() = TestApplicationSupport.withApp { session ->
        // GIVEN
        val nonExistentId = java.util.UUID.randomUUID().toString()

        // WHEN
        val response = client.get("/v1/trips/$nonExistentId") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }

        // THEN
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun given_tripAndMember_when_joinWithInvalidUuidThenValidJoin_then_badRequestThenOk() = TestApplicationSupport.withApp { owner ->
        // GIVEN
        val created = createTrip(
            accessToken = owner.accessToken,
            title = "Joinable trip",
            startDate = "2026-08-10",
            endDate = "2026-08-12",
        )
        val tripId = created["id"]!!.jsonPrimitive.content
        val member = createDevSession(googleId = "member-1", name = "Member One")

        // WHEN
        val invalidJoin = client.post("/v1/trips/not-a-uuid/join") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }
        val join = client.post("/v1/trips/$tripId/join") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }
        val tripForMember = client.get("/v1/trips/$tripId") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }

        // THEN
        assertEquals(HttpStatusCode.BadRequest, invalidJoin.status)
        val invalidJoinBody = json.parseToJsonElement(invalidJoin.body<String>()).jsonObject
        assertEquals("invalid_trip_id", invalidJoinBody["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, join.status)
        assertEquals(HttpStatusCode.OK, tripForMember.status)
        val tripBody = json.parseToJsonElement(tripForMember.body<String>()).jsonObject
        assertNotNull(tripBody["id"])
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

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createTrip(
        accessToken: String,
        title: String,
        startDate: String,
        endDate: String,
    ): kotlinx.serialization.json.JsonObject {
        val response = client.post("/v1/trips") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(
                """
                {
                  "title": "$title",
                  "description": "trip for integration test",
                  "startDate": "$startDate",
                  "endDate": "$endDate",
                  "locationLine": "Paris",
                  "currencyCode": "EUR"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.parseToJsonElement(response.body<String>()).jsonObject
    }
}
