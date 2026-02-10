package nvk.cotrip.ui.navigation

import android.net.Uri

sealed interface Destination {
    val route: String

    data object SignIn : Destination {
        override val route = "auth/sign-in"
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

    data object Notifications : Destination {
        override val route = "notifications"
    }

    data class JoinTrip(val token: String? = null) : Destination {
        override val route: String =
            if (token.isNullOrBlank()) "trips/join" else "trips/join?token=$token"

        companion object {
            const val ARG_INVITE_TOKEN = "inviteToken"
            const val ROUTE_PATTERN = "trips/join?token={$ARG_INVITE_TOKEN}"
        }
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

    data class OutOfRangeDays(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/out-of-range-days"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/out-of-range-days"
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

    data class TripMembers(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/members"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/members"
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

    data class IdeaDetails(val tripId: String, val ideaId: String) : Destination {
        override val route: String = "trips/$tripId/ideas/$ideaId"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/ideas/{ideaId}"
            const val ARG_TRIP_ID = "tripId"
            const val ARG_IDEA_ID = "ideaId"
        }
    }

    data class CreateIdea(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/ideas/create"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/ideas/create"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class EditIdea(val tripId: String, val ideaId: String) : Destination {
        override val route: String = "trips/$tripId/ideas/$ideaId/edit"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/ideas/{ideaId}/edit"
            const val ARG_TRIP_ID = "tripId"
            const val ARG_IDEA_ID = "ideaId"
        }
    }

    data class TripItinerary(
        val tripId: String,
        val requireCities: Boolean = false,
        val creationFlow: Boolean = false,
    ) : Destination {
        override val route: String =
            "trips/$tripId/itinerary?requireCities=$requireCities&creationFlow=$creationFlow"

        companion object {
            const val ROUTE_PATTERN =
                "trips/{tripId}/itinerary?requireCities={requireCities}&creationFlow={creationFlow}"
            const val ARG_TRIP_ID = "tripId"
            const val ARG_REQUIRE_CITIES = "requireCities"
            const val ARG_CREATION_FLOW = "creationFlow"
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

    data class RouteSuggestions(
        val tripId: String,
        val city: String? = null,
        val description: String? = null,
        val typeOptions: List<String> = emptyList(),
        val timeOfDayOptions: List<String> = emptyList(),
        val budgetOptions: List<String> = emptyList(),
    ) : Destination {
        override val route: String = buildString {
            append("trips/$tripId/route-suggestions")
            val params = mutableListOf<String>()
            city?.trim()?.takeIf { it.isNotBlank() }?.let {
                params += "${ARG_CITY}=${Uri.encode(it)}"
            }
            description?.trim()?.takeIf { it.isNotBlank() }?.let {
                params += "${ARG_DESCRIPTION}=${Uri.encode(it)}"
            }
            typeOptions.takeIf { it.isNotEmpty() }?.let {
                params += "${ARG_TYPE_OPTIONS}=${Uri.encode(it.joinToString(","))}"
            }
            timeOfDayOptions.takeIf { it.isNotEmpty() }?.let {
                params += "${ARG_TIME_OF_DAY_OPTIONS}=${Uri.encode(it.joinToString(","))}"
            }
            budgetOptions.takeIf { it.isNotEmpty() }?.let {
                params += "${ARG_BUDGET_OPTIONS}=${Uri.encode(it.joinToString(","))}"
            }
            if (params.isNotEmpty()) {
                append('?')
                append(params.joinToString("&"))
            }
        }

        companion object {
            const val ROUTE_PATTERN =
                "trips/{tripId}/route-suggestions?city={city}&description={description}&typeOptions={typeOptions}&timeOfDayOptions={timeOfDayOptions}&budgetOptions={budgetOptions}"
            const val ARG_TRIP_ID = "tripId"
            const val ARG_CITY = "city"
            const val ARG_DESCRIPTION = "description"
            const val ARG_TYPE_OPTIONS = "typeOptions"
            const val ARG_TIME_OF_DAY_OPTIONS = "timeOfDayOptions"
            const val ARG_BUDGET_OPTIONS = "budgetOptions"
        }
    }

    data class Expenses(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/expenses"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/expenses"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class ExpenseDetails(val tripId: String, val expenseId: String) : Destination {
        override val route: String = "trips/$tripId/expenses/$expenseId"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/expenses/{expenseId}"
            const val ARG_TRIP_ID = "tripId"
            const val ARG_EXPENSE_ID = "expenseId"
        }
    }

    data class CreateExpense(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/expenses/create"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/expenses/create"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class EditExpense(val tripId: String, val expenseId: String) : Destination {
        override val route: String = "trips/$tripId/expenses/$expenseId/edit"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/expenses/{expenseId}/edit"
            const val ARG_TRIP_ID = "tripId"
            const val ARG_EXPENSE_ID = "expenseId"
        }
    }

    data class ActivityDetails(val activityId: String) : Destination {
        override val route: String = "trips/$activityId/activity-details"

        companion object {
            const val ROUTE_PATTERN = "trips/{activityId}/activity-details"
            const val ARG_ACTIVITY_ID = "activityId"
        }
    }

    data class CreateActivity(val tripId: String) : Destination {
        override val route: String = "trips/$tripId/create-activity"

        companion object {
            const val ROUTE_PATTERN = "trips/{tripId}/create-activity"
            const val ARG_TRIP_ID = "tripId"
        }
    }

    data class EditActivity(val activityId: String) : Destination {
        override val route: String = "trips/$activityId/activity-edit"

        companion object {
            const val ROUTE_PATTERN = "trips/{activityId}/activity-edit"
            const val ARG_ACTIVITY_ID = "activityId"
        }
    }
}
