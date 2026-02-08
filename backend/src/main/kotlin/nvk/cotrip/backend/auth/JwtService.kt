package nvk.cotrip.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import nvk.cotrip.backend.config.JwtConfig

object JwtService {
    private lateinit var verifier: JWTVerifier

    fun init(config: JwtConfig) {
        val algorithm = Algorithm.HMAC256(config.secret)
        verifier = JWT.require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()
    }

    fun userIdFromToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return try {
            val decoded = verifier.verify(token)
            decoded.getClaim("userId").asString()
        } catch (_: Exception) {
            null
        }
    }
}
