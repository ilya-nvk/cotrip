package nvk.cotrip.ui.common

import nvk.cotrip.R
import nvk.cotrip.data.network.ApiResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiErrorMapper @Inject constructor() {
    fun messageRes(failure: ApiResult.Failure): Int {
        val code = failure.error?.code.orEmpty()
        return when (failure.error?.code) {
            "invite_invalid",
            "invite_expired",
                -> R.string.join_trip_invalid
            "city_coordinates_required" -> R.string.common_error_city_coordinates_required
            "city_search_error" -> R.string.common_error_city_search
            "weather_provider_unavailable" -> R.string.common_error_weather_unavailable
            "weather_refresh_failed" -> R.string.common_error_weather_refresh

            else -> when {
                failure.httpCode == 401 -> R.string.common_error_unauthorized
                failure.httpCode == 403 -> R.string.common_error_forbidden
                failure.httpCode == 404 -> R.string.common_error_not_found
                failure.cause is IOException -> R.string.common_error_network
                code.startsWith("auth_", ignoreCase = true) -> R.string.common_error_unauthorized
                else -> R.string.common_error_message
            }
        }
    }

    fun messageRes(cause: Throwable): Int {
        return if (cause is IOException) {
            R.string.common_error_network
        } else {
            R.string.common_error_message
        }
    }
}
