package nvk.cotrip.backend.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import nvk.cotrip.backend.limits.LimitReachedException
import nvk.cotrip.backend.limits.toDetailsJson

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<LimitReachedException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                mapOf(
                    "error" to mapOf(
                        "code" to "limit_reached",
                        "message" to "Limit reached for ${cause.entity}",
                        "details" to cause.toDetailsJson(),
                    )
                )
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to mapOf("code" to "bad_request", "message" to cause.message))
            )
        }

        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to mapOf("code" to "bad_request", "message" to cause.message))
            )
        }

        exception<SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to mapOf("code" to "bad_request", "message" to cause.message))
            )
        }

        exception<Throwable> { call, _ ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to mapOf("code" to "internal_error", "message" to "Unexpected error"))
            )
        }
    }
}
