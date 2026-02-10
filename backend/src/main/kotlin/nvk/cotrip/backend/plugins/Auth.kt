package nvk.cotrip.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import nvk.cotrip.backend.config.JwtConfig
import nvk.cotrip.backend.db.UserRepository

fun Application.configureAuth(config: JwtConfig) {
    val algorithm = Algorithm.HMAC256(config.secret)

    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm
            verifier(
                JWT.require(algorithm)
                    .withIssuer(config.issuer)
                    .withAudience(config.audience)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()?.trim()
                if (userId.isNullOrEmpty()) return@validate null
                val isKnownUser = runCatching { UserRepository.findById(userId) != null }
                    .getOrDefault(false)
                if (!isKnownUser) return@validate null
                JWTPrincipal(credential.payload)
            }
        }
    }
}
