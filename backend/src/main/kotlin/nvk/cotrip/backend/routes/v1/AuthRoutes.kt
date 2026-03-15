package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.auth.AuthFlowException
import nvk.cotrip.backend.auth.AuthTokenService
import nvk.cotrip.backend.auth.GoogleTokenVerifier
import nvk.cotrip.backend.config.AppConfig
import nvk.cotrip.backend.db.UserRepository

@Serializable
data class GoogleAuthRequest(
    val idToken: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class DevAuthRequest(
    val googleId: String? = null,
    val name: String? = null,
    val photoUrl: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)

fun Route.authRoutes(appConfig: AppConfig) {
    authenticate("auth-jwt") {
        post("/v1/auth/logout") {
            val principal = call.principal<JWTPrincipal>()
            val sessionId = principal?.getClaim("sessionId", String::class)?.trim()
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            AuthTokenService.revokeSession(sessionId, reason = "logout")
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/v1/auth/google") {
        val request = call.receive<GoogleAuthRequest>()
        val tokenInfo = GoogleTokenVerifier.verify(
            idToken = request.idToken,
            allowedAudiences = appConfig.jwt.googleAllowedAudiences,
        )
        if (tokenInfo == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to mapOf("code" to "invalid_token", "message" to "Invalid Google token")))
            return@post
        }

        val googleId = tokenInfo.sub
        val name = tokenInfo.name ?: tokenInfo.email ?: "User"
        val photoUrl = tokenInfo.picture

        val user = UserRepository.findByGoogleIdAny(googleId)
            ?.let { existing ->
                if (existing.deletedAt != null) {
                    UserRepository.deleteUserAndData(existing.id)
                    null
                } else if (existing.name != name || existing.photoUrl != photoUrl) {
                    UserRepository.updateUser(existing.id, name, photoUrl) ?: existing
                } else {
                    existing
                }
            }
            ?: UserRepository.createUser(googleId, name, photoUrl)

        val tokens = AuthTokenService.issueTokens(user.id)
        call.respond(
            AuthResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                user = user.toDto(),
            )
        )
    }

    post("/v1/auth/dev") {
        if (!appConfig.devAuthEnabled) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }

        val request = call.receive<DevAuthRequest>()
        val googleId = request.googleId ?: "dev-user"
        val name = request.name ?: "Dev User"
        val photoUrl = request.photoUrl

        val user = UserRepository.findByGoogleIdAny(googleId)
            ?.let { existing ->
                if (existing.deletedAt != null) {
                    UserRepository.deleteUserAndData(existing.id)
                    null
                } else if (existing.name != name || existing.photoUrl != photoUrl) {
                    UserRepository.updateUser(existing.id, name, photoUrl) ?: existing
                } else {
                    existing
                }
            }
            ?: UserRepository.createUser(googleId, name, photoUrl)

        val tokens = AuthTokenService.issueTokens(user.id)
        call.respond(
            AuthResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                user = user.toDto(),
            )
        )
    }

    post("/v1/auth/refresh") {
        val request = call.receive<RefreshRequest>()
        val tokens = try {
            AuthTokenService.refreshTokens(request.refreshToken)
        } catch (ex: AuthFlowException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf(
                    "error" to mapOf(
                        "code" to ex.code,
                        "message" to ex.message,
                    )
                )
            )
            return@post
        }
        call.respond(
            RefreshResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            )
        )
    }
}
