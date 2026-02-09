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
    }
}
