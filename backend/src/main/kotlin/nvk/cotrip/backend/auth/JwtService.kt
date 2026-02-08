package nvk.cotrip.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import nvk.cotrip.backend.config.JwtConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

object JwtService {
    private lateinit var verifier: JWTVerifier
    private lateinit var config: JwtConfig
    private lateinit var algorithm: Algorithm

    fun init(config: JwtConfig) {
        this.config = config
        algorithm = Algorithm.HMAC256(config.secret)
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

    fun createToken(userId: String): String {
        val expiresAt = Date.from(Instant.now().plus(30, ChronoUnit.DAYS))
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim("userId", userId)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }
}
