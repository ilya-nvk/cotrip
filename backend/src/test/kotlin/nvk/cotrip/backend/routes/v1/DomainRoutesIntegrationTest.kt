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
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.backend.testing.DbFixtureSupport.createExpense
import nvk.cotrip.backend.testing.DbFixtureSupport.createIdea
import nvk.cotrip.backend.testing.DbFixtureSupport.createTrip
import nvk.cotrip.backend.testing.DbFixtureSupport.joinTrip
import nvk.cotrip.backend.testing.DbFixtureSupport.listItineraryDays
import nvk.cotrip.backend.testing.PostgresIntegrationTest
import nvk.cotrip.backend.testing.TestApplicationSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@PostgresIntegrationTest
class DomainRoutesIntegrationTest {
    private val json = TestApplicationSupport.json

    @Test
    fun given_authenticatedUser_when_updateMeThenDeleteMe_then_meReturnsNotFound() = TestApplicationSupport.withApp { session ->
        // GIVEN
        val accessToken = session.accessToken

        // WHEN
        val me = client.get("/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val updated = client.patch("/v1/users/me") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody("""{"name":"Updated Name","photoUrl":"https://example.test/u.png"}""")
        }
        val deleted = client.delete("/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val afterDelete = client.get("/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        // THEN
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(HttpStatusCode.OK, updated.status)
        val updatedBody = json.parseToJsonElement(updated.body<String>()).jsonObject
        assertEquals("Updated Name", updatedBody["name"]!!.jsonPrimitive.content)
        assertEquals("https://example.test/u.png", updatedBody["photoUrl"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.NotFound, afterDelete.status)
    }

    @Test
    fun given_tripWithOwnerAndMember_when_memberRemovesOwnerThenOwnerRemovesMember_then_forbiddenThenNoContent() = TestApplicationSupport.withApp { owner ->
        // GIVEN
        val trip = createTrip(accessToken = owner.accessToken, title = "Members Flow")
        val tripId = tripId(trip)
        val member = createDevSession(googleId = "member-members", name = "Member Members")
        joinTrip(accessToken = member.accessToken, tripId = tripId)

        // WHEN
        val membersBefore = listMembers(owner.accessToken, tripId)
        val forbidden = client.delete("/v1/trips/$tripId/members/${owner.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }
        val removed = client.delete("/v1/trips/$tripId/members/${member.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }
        val membersAfter = listMembers(owner.accessToken, tripId)

        // THEN
        assertTrue(membersBefore.any { memberId(it.jsonObject) == owner.userId })
        assertTrue(membersBefore.any { memberId(it.jsonObject) == member.userId })
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(1, membersAfter.size)
        assertEquals(owner.userId, memberId(membersAfter.first().jsonObject))
    }

    @Test
    fun given_tripWithMember_when_fullDomainFlow_then_ideasExpensesNotificationsSucceed() = TestApplicationSupport.withApp { owner ->
        // GIVEN
        val trip = createTrip(accessToken = owner.accessToken, title = "Domain Flow")
        val tripId = tripId(trip)
        val member = createDevSession(googleId = "member-domain", name = "Member Domain")
        joinTrip(accessToken = member.accessToken, tripId = tripId)

        // WHEN / THEN — ideas, comments, approve, convert, reject
        val idea = createIdea(
            accessToken = member.accessToken,
            tripId = tripId,
            title = "Louvre visit",
        )
        val ideaId = ideaId(idea)

        val patchIdea = client.patch("/v1/ideas/$ideaId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            setBody("""{"title":"Louvre + dinner","notes":"updated"}""")
        }
        // THEN
        assertEquals(HttpStatusCode.OK, patchIdea.status)

        // WHEN
        val comments = client.get("/v1/ideas/$ideaId/comments?limit=10") {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, comments.status)
        val commentsItems = json.parseToJsonElement(comments.body<String>()).jsonObject["items"]!!.jsonArray
        assertTrue(commentsItems.isNotEmpty())

        val approve = client.post("/v1/ideas/$ideaId/approve") {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, approve.status)

        val days = listItineraryDays(owner.accessToken, tripId)
        assertTrue(days.isNotEmpty())
        val dayId = days.first().jsonObject["id"]!!.jsonPrimitive.content

        val convert = client.post("/v1/ideas/$ideaId/convert-to-activity") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            setBody("""{"dayId":"$dayId","timeText":"10:00"}""")
        }
        assertEquals(HttpStatusCode.NoContent, convert.status)

        val itineraryAfterConvert = listItineraryDays(owner.accessToken, tripId)
        val allActivities = itineraryAfterConvert
            .flatMap { day -> day.jsonObject["activities"]!!.jsonArray }
            .map { it.jsonObject }
        assertTrue(allActivities.any { activity ->
            activity["sourceIdeaId"]?.jsonPrimitive?.content == ideaId
        })

        val reject = client.post("/v1/ideas/$ideaId/reject") {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, reject.status)

        val invalidExpense = client.post("/v1/trips/$tripId/expenses") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            setBody(
                """
                {
                  "title": "Invalid split",
                  "amount": 10.0,
                  "status": "planned",
                  "splitType": "bad",
                  "participants": [{"userId":"${owner.userId}"}]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidExpense.status)

        // WHEN / THEN — expense, settlement, notifications, settings, push tokens
        val expense = createExpense(
            accessToken = owner.accessToken,
            tripId = tripId,
            paidById = owner.userId,
            participants = listOf(owner.userId, member.userId),
            title = "Dinner",
        )
        val expenseId = expenseId(expense)

        val settlement = client.patch("/v1/expenses/$expenseId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            setBody("""{"status":"paid"}""")
        }
        assertEquals(HttpStatusCode.OK, settlement.status)

        val notifications = client.get("/v1/notifications") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, notifications.status)
        val notificationsItems = json.parseToJsonElement(notifications.body<String>()).jsonObject["items"]!!.jsonArray
        assertTrue(notificationsItems.isNotEmpty())
        val firstNotificationId = notificationsItems.first().jsonObject["id"]!!.jsonPrimitive.content

        val markRead = client.patch("/v1/notifications/$firstNotificationId/read") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }
        assertEquals(HttpStatusCode.NoContent, markRead.status)

        val readBulk = client.post("/v1/notifications/read-bulk") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
            setBody("""{"mode":"non_comment"}""")
        }
        assertEquals(HttpStatusCode.OK, readBulk.status)
        val readBulkBody = json.parseToJsonElement(readBulk.body<String>()).jsonObject
        assertNotNull(readBulkBody["updated"])

        val updateSettings = client.patch("/v1/users/me/notification-settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
            setBody("""{"items":[{"key":"expenses_new","enabled":false}]}""")
        }
        assertEquals(HttpStatusCode.OK, updateSettings.status)

        val settings = client.get("/v1/users/me/notification-settings") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, settings.status)
        val settingsItems = json.parseToJsonElement(settings.body<String>()).jsonObject["items"]!!.jsonArray
        assertTrue(
            settingsItems.any { item ->
                item.jsonObject["key"]!!.jsonPrimitive.content == "expenses_new" &&
                    !item.jsonObject["enabled"]!!.jsonPrimitive.content.toBoolean()
            }
        )

        val invalidToken = client.post("/v1/push-tokens") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
            setBody("""{"token":"   ","platform":"android"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidToken.status)

        val pushToken = "token-${member.userId}"
        val upsertToken = client.post("/v1/push-tokens") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
            setBody("""{"token":"$pushToken","platform":"android"}""")
        }
        assertEquals(HttpStatusCode.NoContent, upsertToken.status)

        val deleteToken = client.delete("/v1/push-tokens/$pushToken") {
            header(HttpHeaders.Authorization, "Bearer ${member.accessToken}")
        }
        assertEquals(HttpStatusCode.NoContent, deleteToken.status)
    }

    @Test
    fun given_tripWithIdea_when_syncWithoutSinceThenInvalidSinceThenPaged_then_badRequestThenOkWithCursor() = TestApplicationSupport.withApp { session ->
        // GIVEN
        val trip = createTrip(accessToken = session.accessToken, title = "Sync Pull")
        val tripId = tripId(trip)
        createIdea(accessToken = session.accessToken, tripId = tripId, title = "Sync idea")
        val accessToken = session.accessToken

        // WHEN
        val missingSince = client.get("/v1/sync/changes") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val invalidSince = client.get("/v1/sync/changes?since=invalid") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val page1 = client.get("/v1/sync/changes?since=2020-01-01T00:00:00Z&limit=1") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val page1Body = json.parseToJsonElement(page1.body<String>()).jsonObject
        val cursor = page1Body["nextCursor"]?.jsonPrimitive?.content
        val page2 = if (!cursor.isNullOrBlank()) {
            client.get("/v1/sync/changes?since=2020-01-01T00:00:00Z&limit=1&cursor=$cursor") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        } else null

        // THEN
        assertEquals(HttpStatusCode.BadRequest, missingSince.status)
        assertEquals(HttpStatusCode.BadRequest, invalidSince.status)
        assertEquals(HttpStatusCode.OK, page1.status)
        val page1Items = page1Body["items"]!!.jsonArray
        assertEquals(1, page1Items.size)
        if (page2 != null) {
            assertEquals(HttpStatusCode.OK, page2.status)
            val page2Items = json.parseToJsonElement(page2.body<String>()).jsonObject["items"]!!.jsonArray
            assertTrue(page2Items.isNotEmpty())
        }
    }

    @Test
    fun given_tripWithDates_when_getWeather_then_returnsOk() = TestApplicationSupport.withApp { session ->
        val trip = createTrip(accessToken = session.accessToken, title = "Weather Trip", startDate = "2026-09-10", endDate = "2026-09-12")
        val tripId = tripId(trip)
        val response = client.get("/v1/trips/$tripId/weather?city=Paris") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        assertTrue(body.containsKey("items"))
    }

    @Test
    fun given_authenticatedUser_when_postUploadImageWithInvalidContentType_then_returnsBadRequest() = TestApplicationSupport.withApp { session ->
        val response = client.post("/v1/uploads/images") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            setBody(io.ktor.client.request.forms.MultiPartFormDataContent(
                io.ktor.client.request.forms.formData {
                    append("file", "not-an-image".toByteArray(), io.ktor.http.Headers.build {
                        append(io.ktor.http.HttpHeaders.ContentType, "text/plain")
                    })
                }
            ))
        }
        assertTrue(response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnsupportedMediaType)
    }

    private suspend fun ApplicationTestBuilder.listMembers(
        accessToken: String,
        tripId: String,
    ): JsonArray {
        val response = client.get("/v1/trips/$tripId/members") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.parseToJsonElement(response.body<String>()).jsonObject["items"]!!.jsonArray
    }

    private fun memberId(item: JsonObject): String = item["userId"]!!.jsonPrimitive.content

    private fun tripId(trip: JsonObject): String = trip["id"]!!.jsonPrimitive.content

    private fun ideaId(idea: JsonObject): String = idea["id"]!!.jsonPrimitive.content

    private fun expenseId(expense: JsonObject): String = expense["id"]!!.jsonPrimitive.content

    private suspend fun ApplicationTestBuilder.createDevSession(
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
}
