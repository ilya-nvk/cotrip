package nvk.cotrip.ui.navigation

sealed interface Destination {
    val route: String

    data object SignIn : Destination {
        override val route = "auth/signin"
    }

    data object Trips : Destination {
        override val route = "trips"
    }

    data object CreateTrip : Destination {
        override val route = "trips/create"
    }

    data object Settings : Destination {
        override val route = "settings"
    }

    data class TripDetails(val tripId: String) : Destination {
        override val route: String = "trips/$tripId"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class Expenses(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/expenses"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/expenses"
            const val ARG_TRIP_ID = "tripId"
        }
    }
}