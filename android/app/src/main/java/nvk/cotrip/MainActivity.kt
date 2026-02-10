package nvk.cotrip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.notifications.NotificationIntentExtras
import nvk.cotrip.notifications.SystemNotificationManager
import nvk.cotrip.ui.navigation.AppNavHost
import nvk.cotrip.ui.navigation.AppNavigatorImpl
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.theme.CoTripTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appNavigatorImpl: AppNavigatorImpl

    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var systemNotificationManager: SystemNotificationManager

    private val pendingNotifications = MutableSharedFlow<NotificationNavigationTarget>(
        replay = 0,
        extraBufferCapacity = 1
    )
    private var initialNotificationTarget: NotificationNavigationTarget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialNotificationTarget = notificationTargetFromIntent(intent)
        requestNotificationPermissionIfNeeded()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContent {
            val navController = rememberNavController()
            val startDestination = if (sessionStore.getAccessToken().isNullOrBlank()) {
                Destination.SignIn.route
            } else {
                Destination.Trips.route
            }

            LaunchedEffect(navController) {
                appNavigatorImpl.attachController(navController)
                initialNotificationTarget?.let {
                    pendingNotifications.emit(it)
                    initialNotificationTarget = null
                }
                pendingNotifications.collect { target ->
                    appNavigatorImpl.navigate(target.destination) {
                        launchSingleTop = true
                    }
                    target.notificationId?.let { notificationId ->
                        lifecycleScope.launch {
                            runCatching { notificationRepository.markRead(notificationId) }
                            systemNotificationManager.onMarkedRead(notificationId)
                        }
                    }
                }
            }

            CoTripTheme {
                AppNavHost(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val target = notificationTargetFromIntent(intent) ?: return
        pendingNotifications.tryEmit(target)
    }

    private fun notificationTargetFromIntent(intent: Intent?): NotificationNavigationTarget? {
        val sourceEvent =
            intent?.getStringExtra(NotificationIntentExtras.EXTRA_EVENT) ?: return null
        val notificationId = intent.getStringExtra(NotificationIntentExtras.EXTRA_NOTIFICATION_ID)
        val tripId = intent.getStringExtra(NotificationIntentExtras.EXTRA_TRIP_ID).orEmpty()
        val ideaId = intent.getStringExtra(NotificationIntentExtras.EXTRA_IDEA_ID).orEmpty()
        val expenseId = intent.getStringExtra(NotificationIntentExtras.EXTRA_EXPENSE_ID).orEmpty()
        val destination = when (sourceEvent) {
            "idea_comment", "idea_created" -> {
                if (tripId.isNotBlank() && ideaId.isNotBlank()) {
                    Destination.IdeaDetails(tripId = tripId, ideaId = ideaId)
                } else {
                    Destination.Notifications
                }
            }

            "expense_created", "expense_settlement" -> {
                when {
                    tripId.isNotBlank() && expenseId.isNotBlank() ->
                        Destination.ExpenseDetails(tripId = tripId, expenseId = expenseId)

                    tripId.isNotBlank() -> Destination.Expenses(tripId = tripId)
                    else -> Destination.Notifications
                }
            }

            else -> Destination.Notifications
        }
        return NotificationNavigationTarget(
            destination = destination,
            notificationId = notificationId
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS_PERMISSION
        )
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS_PERMISSION = 1001
    }

    private data class NotificationNavigationTarget(
        val destination: Destination,
        val notificationId: String?,
    )
}
