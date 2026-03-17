package nvk.cotrip.data.network

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ApiCallerTest {
    private val caller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_successfulBlock_when_call_then_returnsSuccessResult() = runTest {
        // GIVEN — caller and block returning 42

        // WHEN
        val result = caller.call { 42 }

        // THEN
        assertTrue(result is ApiResult.Success)
        assertEquals(42, (result as ApiResult.Success).data)
    }

    @Test
    fun given_httpExceptionWithLimitReached_when_call_then_returnsFailureWithParsedError() = runTest {
        // GIVEN
        val errorJson = """
            {"error":{"code":"limit_reached","message":"Too many","details":{"entity":"trip","scopeId":"u","limit":1,"currentCount":1}}}
        """.trimIndent()
        val response = Response.error<String>(
            429,
            errorJson.toResponseBody("application/json".toMediaType()),
        )

        // WHEN
        val result = caller.call<String> { throw HttpException(response) }

        // THEN
        assertTrue(result is ApiResult.Failure)
        val failure = result as ApiResult.Failure
        assertEquals(429, failure.httpCode)
        assertEquals("limit_reached", failure.error?.code)
    }

    @Test
    fun given_ioException_when_call_then_returnsFailureWithCause() = runTest {
        // GIVEN — block that throws IOException

        // WHEN
        val result = caller.call<String> { throw java.io.IOException("offline") }

        // THEN
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).cause is java.io.IOException)
    }

    @Test(expected = CancellationException::class)
    fun given_cancellationException_when_call_then_rethrowsCancellation() = runTest {
        // GIVEN — block that throws CancellationException

        // WHEN
        caller.call<String> { throw CancellationException("cancelled") }

        // THEN — CancellationException propagates
    }
}
