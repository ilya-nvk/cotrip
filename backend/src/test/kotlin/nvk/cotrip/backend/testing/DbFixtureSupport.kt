package nvk.cotrip.backend.testing

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

object DbFixtureSupport {
    private val json get() = TestApplicationSupport.json

    suspend fun ApplicationTestBuilder.createTrip(
        accessToken: String,
        title: String = "Integration Trip",
        startDate: String = "2026-09-10",
        endDate: String = "2026-09-12",
        currencyCode: String = "EUR",
    ): JsonObject {
        val response = client.post("/v1/trips") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(
                """
                {
                  "title": "$title",
                  "description": "created by integration fixtures",
                  "startDate": "$startDate",
                  "endDate": "$endDate",
                  "locationLine": "Paris",
                  "currencyCode": "$currencyCode"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.parseToJsonElement(response.body<String>()).jsonObject
    }

    suspend fun ApplicationTestBuilder.joinTrip(
        accessToken: String,
        tripId: String,
    ) {
        val response = client.post("/v1/trips/$tripId/join") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    suspend fun ApplicationTestBuilder.listItineraryDays(
        accessToken: String,
        tripId: String,
    ): JsonArray {
        val response = client.get("/v1/trips/$tripId/itinerary") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.parseToJsonElement(response.body<String>()).jsonObject["items"]!!.jsonArray
    }

    suspend fun ApplicationTestBuilder.createIdea(
        accessToken: String,
        tripId: String,
        title: String = "Idea from fixture",
    ): JsonObject {
        val response = client.post("/v1/trips/$tripId/ideas") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(
                """
                {
                  "title": "$title",
                  "city": "Paris",
                  "notes": "test idea"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.parseToJsonElement(response.body<String>()).jsonObject
    }

    suspend fun ApplicationTestBuilder.createExpense(
        accessToken: String,
        tripId: String,
        paidById: String,
        participants: List<String>,
        title: String = "Fixture expense",
    ): JsonObject {
        val participantsJson = participants.joinToString(",") { participantId ->
            """{"userId":"$participantId","isIncluded":true,"isPaid":false}"""
        }
        val response = client.post("/v1/trips/$tripId/expenses") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(
                """
                {
                  "title": "$title",
                  "amount": 24.5,
                  "status": "planned",
                  "splitType": "equally",
                  "paidById": "$paidById",
                  "participants": [$participantsJson]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.parseToJsonElement(response.body<String>()).jsonObject
    }

    fun JsonObject.id(): String = this["id"]!!.jsonPrimitive.content
}
