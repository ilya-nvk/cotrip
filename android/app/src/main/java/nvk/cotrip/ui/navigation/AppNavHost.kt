package nvk.cotrip.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nvk.cotrip.ui.auth.SignInScreen
import nvk.cotrip.ui.forecast.TripForecastScreen
import nvk.cotrip.ui.invitation.InvitePeopleScreen
import nvk.cotrip.ui.tripdetails.TripDetailsScreen
import nvk.cotrip.ui.tripform.CreateTripScreen
import nvk.cotrip.ui.tripform.EditTripScreen
import nvk.cotrip.ui.trips.TripsListScreen

const val ARG_TRIP_ID = "tripId"

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
            //SettingsScreen()
        }

        composable(
            route = Destination.TripDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            TripDetailsScreen()
        }

        composable(
            route = Destination.EditTrip.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            EditTripScreen()
        }

        composable(
            route = Destination.InviteTravelers.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            InvitePeopleScreen()
        }

        composable(
            route = Destination.TripIdeas.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //TripIdeasScreen()
        }

        composable(
            route = Destination.TripItinerary.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //TripItineraryScreen()
        }

        composable(
            route = Destination.TripForecast.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            TripForecastScreen()
        }

        composable(
            route = Destination.BuildRoute.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //BuildRouteScreen()
        }

        composable(
            route = Destination.RouteSuggestions.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //RouteSuggestionsScreen()
        }

        composable(
            route = Destination.Expenses.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //ExpensesScreen()
        }
    }
}
