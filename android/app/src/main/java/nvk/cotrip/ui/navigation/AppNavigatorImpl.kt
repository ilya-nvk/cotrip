package nvk.cotrip.ui.navigation

import android.os.SystemClock
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNavigatorImpl @Inject constructor() : AppNavigator {

    private val controllerRef = AtomicReference<NavHostController?>(null)
    private var lastPopRoute: String? = null
    private var lastPopTimestampMs: Long = 0L

    fun attachController(controller: NavHostController) {
        controllerRef.set(controller)
    }

    override fun navigate(destination: Destination, navOptions: (NavOptionsBuilder.() -> Unit)?) {
        val controller = controllerRef.get() ?: return
        controller.safeNavigate(destination.route, navOptions ?: {})
    }

    override fun navigateToSignInClearingFullBackStack() {
        val controller = controllerRef.get() ?: return
        controller.navigate(Destination.SignIn.route) {
            popUpTo(controller.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    override fun popBackStack(): Boolean {
        val controller = controllerRef.get() ?: return false
        val currentRoute = controller.currentBackStackEntry?.destination?.route
        val now = SystemClock.elapsedRealtime()
        val shouldIgnore = synchronized(this) {
            currentRoute != null &&
                currentRoute == lastPopRoute &&
                now - lastPopTimestampMs < POP_BACK_DEBOUNCE_MS
        }
        if (shouldIgnore) return false

        val popped = controller.popBackStack()
        if (popped) {
            synchronized(this) {
                lastPopRoute = currentRoute
                lastPopTimestampMs = now
            }
        }
        return popped
    }
}

private const val POP_BACK_DEBOUNCE_MS = 350L
