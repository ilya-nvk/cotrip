package nvk.cotrip.backend.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoogleTokenVerifierTest {

    @AfterTest
    fun tearDown() {
        GoogleTokenVerifier.httpClientForTest = null
    }

    @Test
    fun given_validTokenInfoWithMatchingAudience_when_verify_then_returnsTokenInfo() = runBlocking {
        val json = """{"sub":"google-123","aud":"my-client-id","name":"Test User","picture":null,"email":"test@example.com"}"""
        val mockEngine = MockEngine {
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        GoogleTokenVerifier.httpClientForTest = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = GoogleTokenVerifier.verify("any-token", setOf("my-client-id"))

        assertEquals("google-123", result?.sub)
        assertEquals("my-client-id", result?.aud)
        assertEquals("Test User", result?.name)
    }

    @Test
    fun given_validTokenInfoWithWrongAudience_when_verify_then_returnsNull() = runBlocking {
        val json = """{"sub":"google-123","aud":"other-client","name":"Test"}"""
        val mockEngine = MockEngine {
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        GoogleTokenVerifier.httpClientForTest = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = GoogleTokenVerifier.verify("any-token", setOf("my-client-id"))

        assertNull(result)
    }

    @Test
    fun given_tokenInfoWithBlankAudience_when_verify_then_returnsNull() = runBlocking {
        val json = """{"sub":"google-123","aud":"  ","name":"Test"}"""
        val mockEngine = MockEngine {
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        GoogleTokenVerifier.httpClientForTest = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = GoogleTokenVerifier.verify("any-token", setOf("my-client-id"))

        assertNull(result)
    }

    @Test
    fun given_validTokenInfoWithEmptyAllowedAudiences_when_verify_then_returnsNull() = runBlocking {
        val json = """{"sub":"google-123","aud":"my-client-id","name":"Test"}"""
        val mockEngine = MockEngine {
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        GoogleTokenVerifier.httpClientForTest = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = GoogleTokenVerifier.verify("any-token", emptySet())

        assertNull(result)
    }

    @Test
    fun given_httpError_when_verify_then_returnsNull() = runBlocking {
        val mockEngine = MockEngine {
            respond("", HttpStatusCode.BadRequest)
        }
        GoogleTokenVerifier.httpClientForTest = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = GoogleTokenVerifier.verify("invalid-token", setOf("my-client-id"))

        assertNull(result)
    }
}
