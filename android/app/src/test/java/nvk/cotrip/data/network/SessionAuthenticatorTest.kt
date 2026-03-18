package nvk.cotrip.data.network

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import nvk.cotrip.data.auth.SessionCleaner
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.dto.RefreshRequest
import nvk.cotrip.data.network.dto.RefreshResponse
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Call

class SessionAuthenticatorTest {
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val authRefreshApi = mockk<AuthRefreshApi>()
    private val sessionCleaner = mockk<SessionCleaner>(relaxed = true)
    private val authenticator = SessionAuthenticator(sessionStore, authRefreshApi, sessionCleaner)

    @Test
    fun given_unauthorizedOnAuthRefreshEndpoint_when_authenticate_then_returnsNull() {
        // GIVEN
        val response = unauthorizedResponse(path = "/v1/auth/refresh", token = "access")

        // WHEN
        val retried = authenticator.authenticate(null, response)

        // THEN
        assertNull(retried)
    }

    @Test
    fun given_tokenAlreadyUpdated_when_authenticate_then_usesLatestTokenWithoutRefresh() {
        // GIVEN
        every { sessionStore.getAccessToken() } returns "latest-access"
        val response = unauthorizedResponse(path = "/v1/trips", token = "stale-access")

        // WHEN
        val retried = authenticator.authenticate(null, response)

        // THEN
        assertNotNull(retried)
        assertEquals("Bearer latest-access", retried!!.header("Authorization"))
        verify(exactly = 0) { authRefreshApi.refresh(any()) }
    }

    @Test
    fun given_refreshSucceeds_when_authenticate_then_updatesSessionAndRetriesWithNewToken() {
        // GIVEN
        every { sessionStore.getAccessToken() } returns "same-access"
        every { sessionStore.getRefreshToken() } returns "refresh-1"
        every { sessionStore.setTokens(any(), any()) } just runs
        val refreshCall = mockk<Call<RefreshResponse>>()
        every { authRefreshApi.refresh(RefreshRequest("refresh-1")) } returns refreshCall
        every { refreshCall.execute() } returns retrofit2.Response.success(
            RefreshResponse(
                accessToken = "new-access",
                refreshToken = "new-refresh",
            )
        )
        val response = unauthorizedResponse(path = "/v1/trips", token = "same-access")

        // WHEN
        val retried = authenticator.authenticate(null, response)

        // THEN
        assertNotNull(retried)
        assertEquals("Bearer new-access", retried!!.header("Authorization"))
        verify { sessionStore.setTokens("new-access", "new-refresh") }
    }

    @Test
    fun given_refreshReturns401_when_authenticate_then_clearsSessionAndReturnsNull() {
        // GIVEN
        every { sessionStore.getAccessToken() } returns "same-access"
        every { sessionStore.getRefreshToken() } returns "refresh-1"
        every { sessionCleaner.clearSessionBlocking() } just runs
        val refreshCall = mockk<Call<RefreshResponse>>()
        every { authRefreshApi.refresh(RefreshRequest("refresh-1")) } returns refreshCall
        every { refreshCall.execute() } returns retrofit2.Response.error(
            401,
            "{}".toResponseBody(),
        )
        val response = unauthorizedResponse(path = "/v1/trips", token = "same-access")

        // WHEN
        val retried = authenticator.authenticate(null, response)

        // THEN
        assertNull(retried)
        verify { sessionCleaner.clearSessionBlocking() }
    }

    private fun unauthorizedResponse(path: String, token: String): Response {
        val request = Request.Builder()
            .url("https://example.com$path")
            .header("Authorization", "Bearer $token")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{}".toResponseBody())
            .build()
    }
}
