package app.cotrip.common.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.cotrip.domain.usecase.RefreshTripsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TripSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshTripsUseCase: RefreshTripsUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        refreshTripsUseCase()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
