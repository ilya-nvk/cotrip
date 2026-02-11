package nvk.cotrip.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import nvk.cotrip.ui.activity.details.ActivityDetailsScreen
import nvk.cotrip.ui.activity.form.CreateActivityScreen
import nvk.cotrip.ui.activity.form.EditActivityScreen
import nvk.cotrip.ui.aisuggestions.BuildRouteScreen
import nvk.cotrip.ui.aisuggestions.RouteSuggestionsScreen
import nvk.cotrip.ui.auth.SignInScreen
import nvk.cotrip.ui.expense.details.ExpenseDetailsScreen
import nvk.cotrip.ui.expense.form.CreateExpenseScreen
import nvk.cotrip.ui.expense.form.EditExpenseScreen
import nvk.cotrip.ui.expense.list.TripExpensesScreen
import nvk.cotrip.ui.forecast.TripForecastScreen
import nvk.cotrip.ui.idea.details.IdeaDetailsScreen
import nvk.cotrip.ui.idea.form.CreateIdeaScreen
import nvk.cotrip.ui.idea.form.EditIdeaScreen
import nvk.cotrip.ui.idea.list.TripIdeasScreen
import nvk.cotrip.ui.invitation.InvitePeopleScreen
import nvk.cotrip.ui.invitation.JoinTripScreen
import nvk.cotrip.ui.itinerary.TripItineraryScreen
import nvk.cotrip.ui.outofrangedays.OutOfRangeDaysScreen
import nvk.cotrip.ui.settings.SettingsScreen
import nvk.cotrip.ui.trip.details.TripDetailsScreen
import nvk.cotrip.ui.trip.form.CreateTripScreen
import nvk.cotrip.ui.trip.form.EditTripScreen
import nvk.cotrip.ui.trip.list.TripsListScreen
import nvk.cotrip.ui.trip.members.TripMembersScreen

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
            SettingsScreen()
        }

        composable(
            route = Destination.JoinTrip.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.JoinTrip.ARG_INVITE_TOKEN) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://api.cotrip.site/invite/{${Destination.JoinTrip.ARG_INVITE_TOKEN}}" },
                navDeepLink { uriPattern = "http://api.cotrip.site/invite/{${Destination.JoinTrip.ARG_INVITE_TOKEN}}" },
                navDeepLink { uriPattern = "https://api.cotrip.site/trips/{${Destination.JoinTrip.ARG_INVITE_TOKEN}}/invite" },
                navDeepLink { uriPattern = "http://api.cotrip.site/trips/{${Destination.JoinTrip.ARG_INVITE_TOKEN}}/invite" },
            )
        ) {
            JoinTripScreen()
        }

        composable(
            route = Destination.TripDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripDetails.ARG_TRIP_ID) { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        "https://api.cotrip.site/trips/{${Destination.TripDetails.ARG_TRIP_ID}}"
                },
                navDeepLink {
                    uriPattern =
                        "http://api.cotrip.site/trips/{${Destination.TripDetails.ARG_TRIP_ID}}"
                },
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
            route = Destination.TripMembers.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripMembers.ARG_TRIP_ID) { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        "https://api.cotrip.site/trips/{${Destination.TripMembers.ARG_TRIP_ID}}/members"
                },
                navDeepLink {
                    uriPattern =
                        "http://api.cotrip.site/trips/{${Destination.TripMembers.ARG_TRIP_ID}}/members"
                },
            )
        ) {
            TripMembersScreen()
        }

        composable(
            route = Destination.TripIdeas.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripIdeas.ARG_TRIP_ID) { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        "https://api.cotrip.site/trips/{${Destination.TripIdeas.ARG_TRIP_ID}}/ideas"
                },
                navDeepLink {
                    uriPattern =
                        "http://api.cotrip.site/trips/{${Destination.TripIdeas.ARG_TRIP_ID}}/ideas"
                },
            )
        ) {
            TripIdeasScreen()
        }

        composable(
            route = Destination.IdeaDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.IdeaDetails.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Destination.IdeaDetails.ARG_IDEA_ID) { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        "https://api.cotrip.site/trips/{${Destination.IdeaDetails.ARG_TRIP_ID}}/ideas/{${Destination.IdeaDetails.ARG_IDEA_ID}}"
                },
                navDeepLink {
                    uriPattern =
                        "http://api.cotrip.site/trips/{${Destination.IdeaDetails.ARG_TRIP_ID}}/ideas/{${Destination.IdeaDetails.ARG_IDEA_ID}}"
                },
            )
        ) {
            IdeaDetailsScreen()
        }

        composable(
            route = Destination.CreateIdea.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.CreateIdea.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            CreateIdeaScreen()
        }

        composable(
            route = Destination.EditIdea.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.EditIdea.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Destination.EditIdea.ARG_IDEA_ID) { type = NavType.StringType }
            )
        ) {
            EditIdeaScreen()
        }

        composable(
            route = Destination.TripItinerary.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.TripItinerary.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Destination.TripItinerary.ARG_REQUIRE_CITIES) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(Destination.TripItinerary.ARG_CREATION_FLOW) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        "https://api.cotrip.site/trips/{${Destination.TripItinerary.ARG_TRIP_ID}}/itinerary"
                },
                navDeepLink {
                    uriPattern =
                        "http://api.cotrip.site/trips/{${Destination.TripItinerary.ARG_TRIP_ID}}/itinerary"
                },
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
            BuildRouteScreen()
        }

        composable(
            route = Destination.RouteSuggestions.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.RouteSuggestions.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Destination.RouteSuggestions.ARG_CITY) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Destination.RouteSuggestions.ARG_DESCRIPTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Destination.RouteSuggestions.ARG_TYPE_OPTIONS) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Destination.RouteSuggestions.ARG_TIME_OF_DAY_OPTIONS) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Destination.RouteSuggestions.ARG_BUDGET_OPTIONS) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            )
        ) {
            RouteSuggestionsScreen()
        }

        composable(
            route = Destination.Expenses.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.Expenses.ARG_TRIP_ID) { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        "https://api.cotrip.site/trips/{${Destination.Expenses.ARG_TRIP_ID}}/expenses"
                },
                navDeepLink {
                    uriPattern =
                        "http://api.cotrip.site/trips/{${Destination.Expenses.ARG_TRIP_ID}}/expenses"
                },
            )
        ) {
            TripExpensesScreen()
        }

        composable(
            route = Destination.ExpenseDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.ExpenseDetails.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Destination.ExpenseDetails.ARG_EXPENSE_ID) { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        "https://api.cotrip.site/trips/{${Destination.ExpenseDetails.ARG_TRIP_ID}}/expenses/{${Destination.ExpenseDetails.ARG_EXPENSE_ID}}"
                },
                navDeepLink {
                    uriPattern =
                        "http://api.cotrip.site/trips/{${Destination.ExpenseDetails.ARG_TRIP_ID}}/expenses/{${Destination.ExpenseDetails.ARG_EXPENSE_ID}}"
                },
            )
        ) {
            ExpenseDetailsScreen()
        }

        composable(
            route = Destination.CreateExpense.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.CreateExpense.ARG_TRIP_ID) { type = NavType.StringType }
            )
        ) {
            CreateExpenseScreen()
        }

        composable(
            route = Destination.EditExpense.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.EditExpense.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Destination.EditExpense.ARG_EXPENSE_ID) { type = NavType.StringType }
            )
        ) {
            EditExpenseScreen()
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
