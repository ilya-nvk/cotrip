package nvk.cotrip.data.repository

/**
 * Thrown after a mutation was applied locally and enqueued for sync.
 * Callers can distinguish this from a completed remote write without inspecting [nvk.cotrip.data.network.NetworkStateProvider].
 */
class OfflineWriteQueuedException(
    message: String = "Mutation queued for sync when offline",
    cause: Throwable? = null,
) : Exception(message, cause)
