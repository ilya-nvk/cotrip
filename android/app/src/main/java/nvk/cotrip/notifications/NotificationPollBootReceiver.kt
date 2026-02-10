package nvk.cotrip.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nvk.cotrip.util.AppLogger

class NotificationPollBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AppLogger.i(TAG, "boot/package replaced, rescheduling alarm")
                NotificationPollAlarm.schedule(context, delayMs = 30_000L)
            }
        }
    }

    private companion object {
        private const val TAG = "NotifBootReceiver"
    }
}

