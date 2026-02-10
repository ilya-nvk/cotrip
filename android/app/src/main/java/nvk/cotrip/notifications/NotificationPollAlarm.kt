package nvk.cotrip.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object NotificationPollAlarm {
    private const val REQUEST_CODE = 7011
    private const val DEFAULT_DELAY_MS = 3 * 60 * 1000L

    fun schedule(context: Context, delayMs: Long = DEFAULT_DELAY_MS) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent(context)
        )
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationPollAlarmReceiver::class.java).apply {
            action = NotificationPollAlarmReceiver.ACTION_POLL_NOTIFICATIONS
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

