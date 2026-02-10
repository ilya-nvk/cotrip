package nvk.cotrip

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nvk.cotrip.data.refresh.RefreshScheduler
import nvk.cotrip.data.repository.PendingTripCreationCleaner
import nvk.cotrip.notifications.AppRuntimeState
import nvk.cotrip.notifications.NotificationPollAlarm
import javax.inject.Inject

@HiltAndroidApp
class CoTripApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    lateinit var refreshScheduler: RefreshScheduler
    @Inject
    lateinit var pendingTripCreationCleaner: PendingTripCreationCleaner

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivities: Int = 0

    private val lifecycleCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities += 1
            AppRuntimeState.setAppForeground(startedActivities > 0)
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            AppRuntimeState.setAppForeground(startedActivities > 0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            // Our backend doesn't send strict cache headers for media.
            // Keep images in disk cache so they remain visible offline.
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(lifecycleCallbacks)
        refreshScheduler.schedule()
        refreshScheduler.scheduleImmediate()
        NotificationPollAlarm.schedule(this)
        appScope.launch {
            pendingTripCreationCleaner.cleanupOnAppStart()
        }
    }
}
