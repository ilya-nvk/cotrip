package nvk.cotrip.data.network

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Singleton
class ApiCaller @Inject constructor(
    private val json: Json,
) {
    suspend fun <T> call(block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (e: HttpException) {
            val apiError = parseError(e)
            ApiResult.Failure(
                error = apiError,
                httpCode = e.code(),
                cause = e,
            )
        } catch (e: IOException) {
            ApiResult.Failure(cause = e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
}
