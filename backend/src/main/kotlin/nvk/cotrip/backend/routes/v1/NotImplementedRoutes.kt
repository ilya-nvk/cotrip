package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private suspend fun respondNotImplemented(call: io.ktor.server.application.ApplicationCall) {
    call.respond(HttpStatusCode.NotImplemented, mapOf("error" to mapOf("code" to "not_implemented", "message" to "Not implemented yet")))
}

fun Route.notImplementedRoutes() {
    route("/v1") {
        post("/auth/logout") { respondNotImplemented(call) }

        get("/trips/{tripId}/members") { respondNotImplemented(call) }
        delete("/trips/{tripId}/members/{userId}") { respondNotImplemented(call) }
        get("/trips/{tripId}/ideas") { respondNotImplemented(call) }
        post("/trips/{tripId}/ideas") { respondNotImplemented(call) }
        get("/ideas/{ideaId}") { respondNotImplemented(call) }
        patch("/ideas/{ideaId}") { respondNotImplemented(call) }
        delete("/ideas/{ideaId}") { respondNotImplemented(call) }
        post("/ideas/{ideaId}/approve") { respondNotImplemented(call) }
        post("/ideas/{ideaId}/reject") { respondNotImplemented(call) }
        post("/ideas/{ideaId}/convert-to-activity") { respondNotImplemented(call) }

        get("/ideas/{ideaId}/comments") { respondNotImplemented(call) }
        delete("/comments/{commentId}") { respondNotImplemented(call) }

        get("/trips/{tripId}/itinerary") { respondNotImplemented(call) }
        patch("/itinerary/days/{dayId}") { respondNotImplemented(call) }
        post("/itinerary/days/{dayId}/activities") { respondNotImplemented(call) }
        patch("/itinerary/activities/{activityId}") { respondNotImplemented(call) }
        delete("/itinerary/activities/{activityId}") { respondNotImplemented(call) }
        post("/itinerary/days/{dayId}/activities/reorder") { respondNotImplemented(call) }
        post("/trips/{tripId}/itinerary/trim-out-of-range") { respondNotImplemented(call) }

        get("/trips/{tripId}/expenses") { respondNotImplemented(call) }
        post("/trips/{tripId}/expenses") { respondNotImplemented(call) }
        get("/expenses/{expenseId}") { respondNotImplemented(call) }
        patch("/expenses/{expenseId}") { respondNotImplemented(call) }
        delete("/expenses/{expenseId}") { respondNotImplemented(call) }

        get("/trips/{tripId}/weather") { respondNotImplemented(call) }
        post("/trips/{tripId}/weather/refresh") { respondNotImplemented(call) }

        post("/trips/{tripId}/ai/suggestions") { respondNotImplemented(call) }
        post("/ai/suggestions/{id}/save-to-ideas") { respondNotImplemented(call) }

        get("/notifications") { respondNotImplemented(call) }
        patch("/notifications/{id}/read") { respondNotImplemented(call) }
        get("/users/me/notification-settings") { respondNotImplemented(call) }
        patch("/users/me/notification-settings") { respondNotImplemented(call) }

        get("/sync/changes") { respondNotImplemented(call) }
        post("/sync/changes") { respondNotImplemented(call) }
    }
}
