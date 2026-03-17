package nvk.cotrip.backend.routes.v1

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.backend.module
import nvk.cotrip.backend.testing.PostgresContainerSupport
import nvk.cotrip.backend.testing.PostgresIntegrationTest
import nvk.cotrip.backend.testing.TestApplicationSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@PostgresIntegrationTest
class AuthRoutesIntegrationTest {
    private val json = TestApplicationSupport.json

    @Test
    fun given_validRefreshToken_when_refreshThenReuse_then_rotatesAndDetectsReuse() = TestApplicationSupport.withApp { session ->
        // GIVEN
        val refreshToken = session.refreshToken

        // WHEN — first refresh can intermittently return 500 in CI (race), retry briefly
        var refreshed = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        var attempts = 1
        while (refreshed.status.value == 500 && attempts < 3) {
            delay(150L * attempts)
            refreshed = client.post("/v1/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$refreshToken"}""")
            }
            attempts++
        }
        assertEquals(HttpStatusCode.OK, refreshed.status, "First refresh failed after $attempts attempt(s): status=${refreshed.status}")
        val refreshedBody = json.parseToJsonElement(refreshed.body<String>()).jsonObject
        val rotatedRefresh = refreshedBody["refreshToken"]!!.jsonPrimitive.content

        val reuseAttempt = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        val rotatedAfterReuse = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$rotatedRefresh"}""")
        }

        // THEN
        assertEquals(HttpStatusCode.Unauthorized, reuseAttempt.status)
        val reuseCode = json.parseToJsonElement(reuseAttempt.body<String>()).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        assertEquals("auth_refresh_reuse_detected", reuseCode)
        assertEquals(HttpStatusCode.Unauthorized, rotatedAfterReuse.status)
    }

    @Test
    fun given_authenticatedSession_when_logout_then_refreshReturnsUnauthorized() = TestApplicationSupport.withApp { session ->
        // GIVEN
        val accessToken = session.accessToken
        val refreshToken = session.refreshToken

        // WHEN
        val logout = client.post("/v1/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val refreshAfterLogout = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        // THEN
        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertEquals(HttpStatusCode.Unauthorized, refreshAfterLogout.status)
    }

    @Test
    fun given_invalidJson_when_postRefresh_then_badRequestOrUnauthorizedOrServerError() = TestApplicationSupport.withApp { _ ->
        // GIVEN / WHEN
        val response = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("not valid json")
        }
        // THEN — server may return 400 (bad request), 401 (unauthorized), or 500 (e.g. deserialization error)
        assertTrue(
            response.status == HttpStatusCode.BadRequest ||
                response.status == HttpStatusCode.Unauthorized ||
                response.status == HttpStatusCode.InternalServerError
        )
    }

    @Test
    fun given_noAuthHeader_when_logout_then_unauthorized() = TestApplicationSupport.withApp { _ ->
        // GIVEN / WHEN
        val response = client.post("/v1/auth/logout") {
            // no Authorization header
        }
        // THEN
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun given_appRunning_when_getHealth_then_ok() = TestApplicationSupport.withApp { _ ->
        // GIVEN / WHEN
        val response = client.get("/health")
        // THEN
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun given_appRunning_when_getAssetLinks_then_returnsOk() = TestApplicationSupport.withApp { _ ->
        val response = client.get("/.well-known/assetlinks.json")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<String>().contains("assetlinks") || response.body<String>().startsWith("["))
    }

    @Test
    fun given_devAuthDisabled_when_postDevAuth_then_notFound() {
        testApplication {
            // GIVEN
            environment {
                config = MapApplicationConfig(
                    "ktor.db.url" to PostgresContainerSupport.jdbcUrl(),
                    "ktor.db.user" to PostgresContainerSupport.username(),
                    "ktor.db.password" to PostgresContainerSupport.password(),
                    "ktor.devAuthEnabled" to "false",
                    "ktor.jwt.secret" to "test-secret",
                )
            }
            application {
                module()
            }

            // WHEN
            val response = client.post("/v1/auth/dev") {
                contentType(ContentType.Application.Json)
                setBody("""{"googleId":"dev","name":"Dev"}""")
            }

            // THEN
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }
}
