package nvk.cotrip

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import nvk.cotrip.data.auth.SessionStore
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            }

            CoTripTheme {
                AppNavHost(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
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
}
