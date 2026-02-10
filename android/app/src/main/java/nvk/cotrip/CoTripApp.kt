package nvk.cotrip

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nvk.cotrip.data.repository.PendingTripCreationCleaner
import javax.inject.Inject
import nvk.cotrip.data.refresh.RefreshScheduler

@HiltAndroidApp
class CoTripApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    lateinit var refreshScheduler: RefreshScheduler
    @Inject
    lateinit var pendingTripCreationCleaner: PendingTripCreationCleaner

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        refreshScheduler.schedule()
        appScope.launch {
            pendingTripCreationCleaner.cleanupOnAppStart()
        }
    }
}
