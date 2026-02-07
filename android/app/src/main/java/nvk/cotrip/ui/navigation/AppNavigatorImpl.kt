package nvk.cotrip.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNavigatorImpl @Inject constructor() : AppNavigator {

    private val controllerRef = AtomicReference<NavHostController?>(null)

    fun attachController(controller: NavHostController) {
        controllerRef.set(controller)
    }

    override fun navigate(destination: Destination, navOptions: (NavOptionsBuilder.() -> Unit)?) {
        val controller = controllerRef.get() ?: return
        controller.safeNavigate(destination.route, navOptions ?: {})
    }

    override fun popBackStack(): Boolean {
        return controllerRef.get()?.popBackStack() ?: false
    }
}
