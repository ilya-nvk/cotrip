package nvk.cotrip.backend.auth

import nvk.cotrip.backend.config.JwtConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtServiceTest {
    private val config = JwtConfig(
        issuer = "test-issuer",
        audience = "test-audience",
        realm = "test-realm",
        secret = "test-secret",
        accessTtlMinutes = 15,
        refreshTtlDays = 30,
        maxActiveSessions = 5,
        googleAllowedAudiences = setOf("aud"),
    )

    @Test
    fun given_validToken_when_parseAccessToken_then_returnsClaims() {
        // GIVEN
        JwtService.init(config)
        val token = JwtService.createAccessToken(
            userId = "user-123",
            sessionId = "session-456",
        )

        // WHEN
        val claims = JwtService.parseAccessToken(token)

        // THEN
        assertNotNull(claims)
        assertEquals("user-123", claims!!.userId)
        assertEquals("session-456", claims.sessionId)
    }

    @Test
    fun given_invalidOrNullToken_when_parseAccessToken_then_returnsNull() {
        // GIVEN
        JwtService.init(config)

        // WHEN / THEN
        assertNull(JwtService.parseAccessToken("broken-token"))
        assertNull(JwtService.parseAccessToken(" "))
        assertNull(JwtService.parseAccessToken(null))
    }

    @Test
    fun given_tokenWithWrongSignature_when_parseAccessToken_then_returnsNull() {
        // GIVEN
        JwtService.init(config)
        val validToken = JwtService.createAccessToken("user-1", "session-1")
        val tamperedToken = validToken.dropLast(1) + "x"

        // WHEN
        val parsed = JwtService.parseAccessToken(tamperedToken)

        // THEN
        assertNull(parsed)
    }

    @Test
    fun given_init_when_verifier_then_returnsNonNull() {
        JwtService.init(config)
        assertNotNull(JwtService.verifier())
    }

    @Test
    fun given_validParams_when_createAccessToken_then_returnsNonBlankJwt() {
        JwtService.init(config)
        val token = JwtService.createAccessToken("user-1", "session-1")
        assertNotNull(token)
        assert(token.isNotBlank())
        assert(token.split(".").size == 3) { "JWT has 3 segments" }
    }
}
