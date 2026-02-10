package nvk.cotrip.data.network

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException

@Singleton
class ApiCaller @Inject constructor(
    private val json: Json,
) {
    suspend fun <T> call(block: suspend () -> T): ApiResult<T> {
        val operation = callerHint()
        return try {
            ApiResult.Success(block())
        } catch (e: HttpException) {
            val apiError = parseError(e)
            AppLogger.w(
                TAG,
                "HTTP ${e.code()} in $operation, apiCode=${apiError?.code.orEmpty()}",
                e
            )
            ApiResult.Failure(
                error = apiError,
                httpCode = e.code(),
                cause = e,
            )
        } catch (e: IOException) {
            AppLogger.w(TAG, "Network error in $operation", e)
            ApiResult.Failure(cause = e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unexpected error in $operation", e)
            ApiResult.Failure(cause = e)
        }
    }

    private fun parseError(exception: HttpException): ApiError? {
        val raw = try {
            exception.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<ApiErrorResponse>(raw).error }.getOrNull()
    }

    private fun callerHint(): String {
        val element = Throwable().stackTrace.firstOrNull {
            it.className.startsWith("nvk.cotrip") && !it.className.contains("ApiCaller")
        } ?: return "unknown"
        return "${element.className.substringAfterLast('.')}.${element.methodName}"
    }

    private companion object {
        private const val TAG = "ApiCaller"
    }
}
