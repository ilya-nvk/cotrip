package nvk.cotrip

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import nvk.cotrip.ui.navigation.AppNavHost
import nvk.cotrip.ui.navigation.AppNavigatorImpl
import dagger.hilt.android.AndroidEntryPoint
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.theme.CoTripTheme
import javax.inject.Inject
import androidx.core.view.WindowCompat

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appNavigatorImpl: AppNavigatorImpl

    @Inject
    lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
