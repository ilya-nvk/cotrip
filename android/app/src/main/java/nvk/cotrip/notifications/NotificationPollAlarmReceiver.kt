package nvk.cotrip.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import nvk.cotrip.data.refresh.RefreshWorker
import nvk.cotrip.util.AppLogger

class NotificationPollAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_POLL_NOTIFICATIONS) return
        AppLogger.i(TAG, "alarm fired, scheduling refresh worker")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ALARM_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
        NotificationPollAlarm.schedule(context)
    }

    companion object {
        const val ACTION_POLL_NOTIFICATIONS = "nvk.cotrip.action.POLL_NOTIFICATIONS"
        private const val TAG = "NotifAlarmReceiver"
        private const val UNIQUE_ALARM_WORK = "refresh-from-alarm"
    }
}

