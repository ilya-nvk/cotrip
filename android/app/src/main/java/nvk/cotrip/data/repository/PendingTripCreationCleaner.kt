package nvk.cotrip.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.util.AppLogger

@Singleton
class PendingTripCreationCleaner @Inject constructor(
    private val sessionStore: SessionStore,
    private val tripRepository: TripRepository,
    private val pendingTripCreationStore: PendingTripCreationStore,
) {
    suspend fun cleanupOnAppStart() {
        val pendingTripId = pendingTripCreationStore.getPendingTripId() ?: return
        if (sessionStore.getAccessToken().isNullOrBlank()) {
            AppLogger.w(TAG, "Skipping pending trip cleanup: no active session")
            return
        }

        runCatching {
            tripRepository.deleteTrip(pendingTripId)
            pendingTripCreationStore.clearPendingTripId(pendingTripId)
            AppLogger.i(TAG, "Deleted stale pending tripId=$pendingTripId on app start")
        }.onFailure {
            AppLogger.w(TAG, "Failed to cleanup pending tripId=$pendingTripId", it)
        }
    }

    private companion object {
        private const val TAG = "PendingTripCleaner"
    }
}
