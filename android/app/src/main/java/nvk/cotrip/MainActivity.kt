package nvk.cotrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import nvk.cotrip.ui.navigation.AppNavHost
import nvk.cotrip.ui.navigation.AppNavigatorImpl
import dagger.hilt.android.AndroidEntryPoint
import nvk.cotrip.ui.theme.CoTripTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appNavigatorImpl: AppNavigatorImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            LaunchedEffect(navController) {
                appNavigatorImpl.attachController(navController)
            }

            CoTripTheme {
                AppNavHost(navController = navController)
            }
        }
    }
}