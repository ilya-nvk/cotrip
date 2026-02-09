package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.db.UserRepository

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val photoUrl: String? = null,
)

fun Route.userRoutes() {
    authenticate("auth-jwt") {
        get("/v1/users/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val user = UserRepository.findById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(user.toDto())
        }

        patch("/v1/users/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val request = call.receive<UpdateUserRequest>()
            val existing = UserRepository.findById(userId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            val updated = UserRepository.updateUser(
                userId = userId,
                name = request.name ?: existing.name,
                photoUrl = request.photoUrl ?: existing.photoUrl,
            )

            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            call.respond(updated.toDto())
        }

        delete("/v1/users/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val deleted = UserRepository.hardDelete(userId)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
