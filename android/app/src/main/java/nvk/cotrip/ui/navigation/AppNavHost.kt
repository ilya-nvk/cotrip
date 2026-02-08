package nvk.cotrip.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nvk.cotrip.ui.activitydetails.ActivityDetailsScreen
import nvk.cotrip.ui.activityform.CreateActivityScreen
import nvk.cotrip.ui.activityform.EditActivityScreen
import nvk.cotrip.ui.auth.SignInScreen
import nvk.cotrip.ui.forecast.TripForecastScreen
import nvk.cotrip.ui.invitation.InvitePeopleScreen
import nvk.cotrip.ui.itinerary.TripItineraryScreen
import nvk.cotrip.ui.outofrangedays.OutOfRangeDaysScreen
import nvk.cotrip.ui.tripdetails.TripDetailsScreen
import nvk.cotrip.ui.tripform.CreateTripScreen
import nvk.cotrip.ui.tripform.EditTripScreen
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
            //SettingsScreen()
        }

        composable(
            route = Destination.TripDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripDetails.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            TripDetailsScreen()
        }

        composable(
            route = Destination.EditTrip.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.EditTrip.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            EditTripScreen()
        }

        composable(
            route = Destination.OutOfRangeDays.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.OutOfRangeDays.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            OutOfRangeDaysScreen()
        }

        composable(
            route = Destination.InviteTravelers.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.InviteTravelers.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            InvitePeopleScreen()
        }

        composable(
            route = Destination.TripIdeas.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripIdeas.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //TripIdeasScreen()
        }

        composable(
            route = Destination.TripItinerary.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripItinerary.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            TripItineraryScreen()
        }

        composable(
            route = Destination.TripForecast.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripForecast.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            TripForecastScreen()
        }

        composable(
            route = Destination.BuildRoute.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.BuildRoute.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //BuildRouteScreen()
        }

        composable(
            route = Destination.RouteSuggestions.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.RouteSuggestions.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //RouteSuggestionsScreen()
        }

        composable(
            route = Destination.Expenses.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.Expenses.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            //ExpensesScreen()
        }

        composable(
            route = Destination.ActivityDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.ActivityDetails.ARG_ACTIVITY_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ActivityDetailsScreen()
        }

        composable(
            route = Destination.CreateActivity.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.CreateActivity.ARG_TRIP_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            CreateActivityScreen()
        }

        composable(
            route = Destination.EditActivity.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.EditActivity.ARG_ACTIVITY_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            EditActivityScreen()
        }
    }
}
