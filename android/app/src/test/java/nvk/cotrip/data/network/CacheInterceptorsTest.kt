package nvk.cotrip.data.network

import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CacheInterceptorsTest {
    @Test
    fun given_online_when_offlineCacheInterceptorIntercept_then_requestHasNoCache() {
        // GIVEN
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        val interceptor = OfflineCacheInterceptor(networkStateProvider)
        val request = Request.Builder().url("https://example.com/v1/trips").get().build()
        val chain = FakeChain(request)

        // WHEN
        val response = interceptor.intercept(chain)

        // THEN
        assertTrue(response.request.cacheControl.noCache)
    }

    @Test
    fun given_offline_when_offlineCacheInterceptorIntercept_then_requestHasOnlyIfCached() {
        // GIVEN
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns false
        val interceptor = OfflineCacheInterceptor(networkStateProvider)
        val request = Request.Builder().url("https://example.com/v1/trips").get().build()
        val chain = FakeChain(request)

        // WHEN
        val response = interceptor.intercept(chain)

        // THEN
        assertTrue(response.request.cacheControl.onlyIfCached)
    }

    @Test
    fun given_getRequest_when_cacheControlInterceptorIntercept_then_responseHasCacheControlHeader() {
        // GIVEN
        val interceptor = CacheControlInterceptor()
        val getChain = FakeChain(
            Request.Builder().url("https://example.com/v1/trips").get().build()
        )

        // WHEN
        val getResponse = interceptor.intercept(getChain)

        // THEN
        assertEquals("public, max-age=120", getResponse.header("Cache-Control"))
    }

    @Test
    fun given_postRequest_when_cacheControlInterceptorIntercept_then_responseHasNoCacheControlHeader() {
        // GIVEN
        val interceptor = CacheControlInterceptor()
        val postChain = FakeChain(
            Request.Builder()
                .url("https://example.com/v1/trips")
                .post("{}".toRequestBody())
                .build()
        )

        // WHEN
        val postResponse = interceptor.intercept(postChain)

        // THEN
        assertEquals(null, postResponse.header("Cache-Control"))
    }

    private class FakeChain(
        private val initialRequest: Request,
    ) : Interceptor.Chain {
        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
