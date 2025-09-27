package app.cotrip.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.cotrip.ui.home.HomeScreen
import app.cotrip.ui.home.HomeViewModel

@Composable
fun CoTripNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = CoTripDestinations.HOME,
        modifier = modifier
    ) {
        composable(CoTripDestinations.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(viewModel = viewModel)
        }
    }
}
