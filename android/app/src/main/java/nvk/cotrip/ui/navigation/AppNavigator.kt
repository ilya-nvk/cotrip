package nvk.cotrip.ui.navigation

import androidx.navigation.NavOptionsBuilder

interface AppNavigator {
    fun navigate(destination: Destination, navOptions: (NavOptionsBuilder.() -> Unit)? = null)
    fun popBackStack(): Boolean

    fun navigateToSignInClearingFullBackStack() {
        navigate(Destination.SignIn) {
            popUpTo(Destination.Trips.route) { inclusive = true }
            launchSingleTop = true
        }
    }
}
