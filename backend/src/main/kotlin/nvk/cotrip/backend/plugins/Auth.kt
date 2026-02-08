package nvk.cotrip.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import nvk.cotrip.backend.config.JwtConfig

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
                if (credential.payload.getClaim("userId").asString().isNullOrBlank()) {
                    null
                } else {
                    JWTPrincipal(credential.payload)
                }
            }
        }
    }
}
