package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.auth.GoogleTokenVerifier
import nvk.cotrip.backend.auth.JwtService
import nvk.cotrip.backend.db.UserRepository

@Serializable
data class GoogleAuthRequest(
    val idToken: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val user: UserDto,
)

fun Route.authRoutes() {
    post("/v1/auth/google") {
        val request = call.receive<GoogleAuthRequest>()
        val tokenInfo = GoogleTokenVerifier.verify(request.idToken)
        if (tokenInfo == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to mapOf("code" to "invalid_token", "message" to "Invalid Google token")))
            return@post
        }

        val googleId = tokenInfo.sub
        val name = tokenInfo.name ?: tokenInfo.email ?: "User"
        val photoUrl = tokenInfo.picture

        val user = UserRepository.findByGoogleId(googleId)
            ?.let { existing ->
                if (existing.name != name || existing.photoUrl != photoUrl) {
                    UserRepository.updateUser(existing.id, name, photoUrl) ?: existing
                } else {
                    existing
                }
            }
            ?: UserRepository.createUser(googleId, name, photoUrl)

        val token = JwtService.createToken(user.id)
        call.respond(AuthResponse(accessToken = token, user = user.toDto()))
    }
}
