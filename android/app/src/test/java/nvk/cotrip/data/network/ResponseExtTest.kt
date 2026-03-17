package nvk.cotrip.data.network

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ResponseExtTest {

    @Test
    fun given_successfulResponse_when_requireSuccess_then_doesNotThrow() {
        // GIVEN
        val response = Response.success(Unit)

        // WHEN
        response.requireSuccess()

        // THEN — no exception
    }

    @Test(expected = HttpException::class)
    fun given_errorResponse_when_requireSuccess_then_throwsHttpException() {
        // GIVEN
        val response = Response.error<Unit>(500, "error".toResponseBody(null))

        // WHEN
        response.requireSuccess()

        // THEN — HttpException thrown
    }
}
