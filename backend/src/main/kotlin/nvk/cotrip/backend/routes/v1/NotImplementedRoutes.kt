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
