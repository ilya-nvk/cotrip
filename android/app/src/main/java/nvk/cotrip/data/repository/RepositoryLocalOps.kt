package nvk.cotrip.data.repository

import nvk.cotrip.util.AppLogger

private const val TAG = "RepositoryLocal"

internal suspend inline fun safeLocalMutation(
    operation: String,
    crossinline block: suspend () -> Unit,
) {
    runCatching { block() }
        .onFailure { error ->
            AppLogger.w(TAG, "Local operation failed: $operation", error)
        }
}
