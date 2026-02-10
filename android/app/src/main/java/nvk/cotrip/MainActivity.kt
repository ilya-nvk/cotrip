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
import nvk.cotrip.notifications.NotificationNavigationState
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

    private val pendingNotificationTaps = MutableSharedFlow<NotificationTap>(
        replay = 0,
        extraBufferCapacity = 1
    )
    private val pendingDeepLinks = MutableSharedFlow<Intent>(
        replay = 0,
        extraBufferCapacity = 1
    )
    private var initialNotificationTap: NotificationTap? = null
    private var initialDeepLinkIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialNotificationTap = notificationTapFromIntent(intent)
        initialDeepLinkIntent = intent.takeIf { it.isSupportedDeepLinkIntent() }
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
                initialDeepLinkIntent?.let {
                    pendingDeepLinks.emit(it)
                    initialDeepLinkIntent = null
                }
                initialNotificationTap?.let {
                    pendingNotificationTaps.emit(it)
                    initialNotificationTap = null
                }
            }

            LaunchedEffect(navController) {
                pendingDeepLinks.collect { deepLinkIntent ->
                    navController.handleDeepLink(deepLinkIntent)
                }
            }

            LaunchedEffect(Unit) {
                pendingNotificationTaps.collect { tap ->
                    tap.notificationId?.let { notificationId ->
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
        notificationTapFromIntent(intent)?.let { tap ->
            pendingNotificationTaps.tryEmit(tap)
        }
        if (intent.isSupportedDeepLinkIntent()) {
            pendingDeepLinks.tryEmit(intent)
        }
    }

    private fun notificationTapFromIntent(intent: Intent?): NotificationTap? {
        val notificationId = intent?.getStringExtra(NotificationIntentExtras.EXTRA_NOTIFICATION_ID)
        val event = intent?.getStringExtra(NotificationIntentExtras.EXTRA_EVENT)
        val ideaId = intent?.getStringExtra(NotificationIntentExtras.EXTRA_IDEA_ID)
        if (event == "idea_comment" && !ideaId.isNullOrBlank()) {
            NotificationNavigationState.requestOpenDiscussion(ideaId)
        }
        if (notificationId.isNullOrBlank() && event.isNullOrBlank()) return null
        return NotificationTap(
            notificationId = notificationId,
            event = event,
            ideaId = ideaId
        )
    }

    private fun Intent?.isSupportedDeepLinkIntent(): Boolean {
        return this?.action == Intent.ACTION_VIEW && this.data != null
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

    private data class NotificationTap(
        val notificationId: String?,
        val event: String?,
        val ideaId: String?,
    )
}
