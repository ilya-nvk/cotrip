package nvk.cotrip.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import nvk.cotrip.ui.auth.SignInScreen
import nvk.cotrip.ui.tripform.CreateTripScreen
import nvk.cotrip.ui.trips.TripsListScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Destination.SignIn.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        composable(Destination.SignIn.route) {
            SignInScreen()
        }

        composable(Destination.Trips.route) {
            TripsListScreen()
        }

        composable(Destination.CreateTrip.route) {
            CreateTripScreen()
        }

        composable(Destination.Settings.route) {
        }

        composable(Destination.TripDetails.ROUTE_PATTERN) { backStackEntry ->
//            val tripId = backStackEntry.arguments
//                ?.requireStringArg(Destination.TripDetails.ARG_TRIP_ID)

//            if (tripId == null) {
//                // null-safe: если аргумента нет — уходим назад, не падаем
//                LaunchedEffect(Unit) { navController.popBackStack() }
//                return@composable
//            }
//
//            TripDetailsScreen(
//                tripId = tripId,
//                onOpenExpenses = {
//                    navController.navigate(Destination.Expenses(tripId).route)
//                },
//                onBack = { navController.popBackStack() }
//            )
        }

        composable(Destination.Expenses.ROUTE_PATTERN) { backStackEntry ->
//            val tripId = backStackEntry.arguments
//                ?.requireStringArg(Destination.Expenses.ARG_TRIP_ID)
//
//            if (tripId == null) {
//                LaunchedEffect(Unit) { navController.popBackStack() }
//                return@composable
//            }
//
//            ExpensesScreen(
//                tripId = tripId,
//                onBack = { navController.popBackStack() }
//            )
        }
    }
}