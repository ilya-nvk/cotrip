package nvk.cotrip.backend.auth

import nvk.cotrip.backend.config.JwtConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthTokenServiceTest {

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
    fun given_blankRefreshToken_when_refreshTokens_then_throwsAuthFlowException() {
        // GIVEN
        AuthTokenService.init(config)

        // WHEN / THEN
        val ex = assertFailsWith<AuthFlowException> {
            AuthTokenService.refreshTokens("")
        }
        assertEquals("auth_refresh_invalid", ex.code)
        assertEquals("Invalid refresh token", ex.message)
    }

    @Test
    fun given_whitespaceOnlyRefreshToken_when_refreshTokens_then_throwsAuthFlowException() {
        // GIVEN
        AuthTokenService.init(config)

        // WHEN / THEN
        val ex = assertFailsWith<AuthFlowException> {
            AuthTokenService.refreshTokens("   ")
        }
        assertEquals("auth_refresh_invalid", ex.code)
    }

    @Test
    fun given_nullOrInvalidAccessToken_when_authenticateAccessToken_then_returnsNull() {
        // GIVEN
        JwtService.init(config)
        AuthTokenService.init(config)

        // WHEN / THEN
        assertNull(AuthTokenService.authenticateAccessToken(null))
        assertNull(AuthTokenService.authenticateAccessToken("invalid-jwt"))
        assertNull(AuthTokenService.authenticateAccessToken(""))
    }
}
