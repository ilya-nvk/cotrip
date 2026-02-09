package nvk.cotrip.ui.common

import javax.inject.Inject
import javax.inject.Singleton
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiResult

@Singleton
class UiErrorMapper @Inject constructor() {
    fun messageRes(failure: ApiResult.Failure): Int {
        return when (failure.error?.code) {
            else -> R.string.common_error_message
        }
    }
}
