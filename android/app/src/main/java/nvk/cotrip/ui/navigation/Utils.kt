package nvk.cotrip.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

fun NavHostController.safeNavigate(
    route: String,
    navOptions: (NavOptionsBuilder.() -> Unit)? = null
) {
    val currentRoute = currentBackStackEntry?.destination?.route
    if (currentRoute == route) return

    if (navOptions != null) navigate(route, navOptions) else navigate(route)
}
