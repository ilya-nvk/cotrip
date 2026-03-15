package nvk.cotrip.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import nvk.cotrip.backend.auth.AuthTokenService
import nvk.cotrip.backend.auth.JwtService
import nvk.cotrip.backend.config.JwtConfig
import nvk.cotrip.backend.db.UserRepository

fun Application.configureAuth(config: JwtConfig) {

    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm
            verifier(JwtService.verifier())
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()?.trim()
                val sessionId = credential.payload.getClaim("sessionId").asString()?.trim()
                val tokenType = credential.payload.getClaim("tokenType").asString()?.trim()
                if (tokenType != "access") return@validate null
                if (userId.isNullOrEmpty() || sessionId.isNullOrEmpty()) return@validate null

                val sessionActive = runCatching {
                    AuthTokenService.isSessionActiveForUser(sessionId, userId)
                }.getOrDefault(false)
                if (!sessionActive) return@validate null

                val isKnownUser = runCatching { UserRepository.findById(userId) != null }
                    .getOrDefault(false)
                if (!isKnownUser) return@validate null
                JWTPrincipal(credential.payload)
            }
        }
    }
}
