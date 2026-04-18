package nvk.cotrip.ui.common

import io.mockk.every
import io.mockk.mockk
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiError
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.NetworkStateProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class UiErrorMapperTest {
    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val mapper = UiErrorMapper(networkStateProvider)

    @Test
    fun given_failureWithKnownApiCode_when_messageRes_then_returnsDedicatedString() {
        // GIVEN
        val failure = ApiResult.Failure(
            error = ApiError(code = "ai_generation_failed", message = "x"),
        )

        // WHEN
        val res = mapper.messageRes(failure)

        // THEN
        assertEquals(R.string.common_error_ai_generation, res)
    }

    @Test
    fun given_newAiApiCodes_when_messageRes_then_returnsDedicatedStrings() {
        assertEquals(
            R.string.common_error_ai_policy_violation,
            mapper.messageRes(ApiResult.Failure(error = ApiError(code = "ai_policy_violation", message = "x"))),
        )
        assertEquals(
            R.string.common_error_ai_no_relevant_results,
            mapper.messageRes(ApiResult.Failure(error = ApiError(code = "ai_no_relevant_results", message = "x"))),
        )
    }

    @Test
    fun given_failureWithAuthPrefix_when_messageRes_then_returnsUnauthorized() {
        // GIVEN
        val failure = ApiResult.Failure(
            error = ApiError(code = "auth_refresh_invalid", message = "x"),
        )

        // WHEN
        val res = mapper.messageRes(failure)

        // THEN
        assertEquals(R.string.common_error_unauthorized, res)
    }

    @Test
    fun given_ioFailureAndOffline_when_messageRes_then_returnsNetworkError() {
        // GIVEN
        val offlineFailure = ApiResult.Failure(cause = java.io.IOException("offline"))
        every { networkStateProvider.isOnline() } returns false

        // WHEN
        val res = mapper.messageRes(offlineFailure)

        // THEN
        assertEquals(R.string.common_error_network, res)
    }

    @Test
    fun given_ioFailureAndOnline_when_messageRes_then_returnsServerUnreachable() {
        // GIVEN
        val serverFailure = ApiResult.Failure(cause = java.io.IOException("server"))
        every { networkStateProvider.isOnline() } returns true

        // WHEN
        val res = mapper.messageRes(serverFailure)

        // THEN
        assertEquals(R.string.common_error_server_unreachable, res)
    }

    @Test
    fun given_failureWithHttpCode_when_messageRes_then_returnsMatchingFallback() {
        // GIVEN / WHEN / THEN
        assertEquals(
            R.string.common_error_not_found,
            mapper.messageRes(ApiResult.Failure(httpCode = 404)),
        )
        assertEquals(
            R.string.common_error_forbidden,
            mapper.messageRes(ApiResult.Failure(httpCode = 403)),
        )
        assertEquals(
            R.string.common_error_unauthorized,
            mapper.messageRes(ApiResult.Failure(httpCode = 401)),
        )
    }
}
