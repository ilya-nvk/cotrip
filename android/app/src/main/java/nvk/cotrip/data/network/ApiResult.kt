package nvk.cotrip.data.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(
        val error: ApiError? = null,
        val httpCode: Int? = null,
        val cause: Throwable? = null,
    ) : ApiResult<Nothing>()
}
