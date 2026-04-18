package nvk.cotrip.data.network

import io.mockk.every
import io.mockk.mockk
import nvk.cotrip.data.auth.SessionStore
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.concurrent.TimeUnit

class AuthInterceptorTest {

    @Test
    fun given_noToken_when_intercept_then_requestHasNoAuthorizationHeader() {
        // GIVEN
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.getAccessToken() } returns null
        val interceptor = AuthInterceptor(sessionStore)
        val request = Request.Builder().url("https://api.example.com/v1/trips").get().build()
        val chain = CapturingChain(request)

        // WHEN
        interceptor.intercept(chain)

        // THEN
        assertNull(chain.proceededRequest?.header("Authorization"))
        assertEquals(
            Locale.getDefault().toLanguageTag(),
            chain.proceededRequest?.header("Accept-Language"),
        )
    }

    @Test
    fun given_blankToken_when_intercept_then_requestHasNoAuthorizationHeader() {
        // GIVEN
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.getAccessToken() } returns ""
        val interceptor = AuthInterceptor(sessionStore)
        val request = Request.Builder().url("https://api.example.com/v1/trips").get().build()
        val chain = CapturingChain(request)

        // WHEN
        interceptor.intercept(chain)

        // THEN
        assertNull(chain.proceededRequest?.header("Authorization"))
        assertEquals(
            Locale.getDefault().toLanguageTag(),
            chain.proceededRequest?.header("Accept-Language"),
        )
    }

    @Test
    fun given_validToken_when_intercept_then_requestHasBearerAuthorizationHeader() {
        // GIVEN
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.getAccessToken() } returns "access-token-123"
        val interceptor = AuthInterceptor(sessionStore)
        val request = Request.Builder().url("https://api.example.com/v1/trips").get().build()
        val chain = CapturingChain(request)

        // WHEN
        interceptor.intercept(chain)

        // THEN
        assertEquals("Bearer access-token-123", chain.proceededRequest?.header("Authorization"))
    }

    private class CapturingChain(
        private val initialRequest: Request,
    ) : Interceptor.Chain {
        var proceededRequest: Request? = null
            private set

        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }

        override fun connection() = null
        override fun call() = throw UnsupportedOperationException("Not needed")
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
}
