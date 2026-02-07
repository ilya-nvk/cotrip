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

    data class EditTrip(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/edit"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/edit"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class InviteTravelers(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/invite"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/invite"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class TripIdeas(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/ideas"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/ideas"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class TripItinerary(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/itinerary"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/itinerary"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class TripForecast(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/forecast"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/forecast"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class BuildRoute(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/build-route"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/build-route"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class RouteSuggestions(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/route-suggestions"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/route-suggestions"
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
