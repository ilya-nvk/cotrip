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
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.db.PushTokenRepository
import nvk.cotrip.backend.db.UserRepository

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val photoUrl: String? = null,
)

@Serializable
data class UpsertPushTokenRequest(
    val token: String,
    val platform: String = "android",
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

            val normalizedName = request.name?.trim()?.takeIf { it.isNotBlank() } ?: existing.name
            val normalizedPhotoUrl = when (val raw = request.photoUrl) {
                null -> existing.photoUrl
                else -> raw.trim().takeIf { it.isNotBlank() }
            }

            val updated = UserRepository.updateUser(
                userId = userId,
                name = normalizedName,
                photoUrl = normalizedPhotoUrl,
            )

            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            call.respond(updated.toDto())
        }

        post("/v1/users/me/push-token") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = call.receive<UpsertPushTokenRequest>()
            val token = request.token.trim()
            val platform = request.platform.trim().ifBlank { "android" }

            if (token.isBlank() || token.length < 20) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            PushTokenRepository.upsert(
                userId = userId,
                token = token,
                platform = platform
            )
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/v1/users/me/push-token") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val token = call.request.queryParameters["token"]?.trim().orEmpty()
            if (token.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            PushTokenRepository.deleteByUserAndToken(userId = userId, token = token)
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/v1/users/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val deleted = UserRepository.deleteUserAndData(userId)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
