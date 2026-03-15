package nvk.cotrip.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import nvk.cotrip.backend.config.JwtConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

object JwtService {
    private lateinit var accessVerifier: JWTVerifier
    private lateinit var config: JwtConfig
    private lateinit var algorithm: Algorithm

    fun init(config: JwtConfig) {
        this.config = config
        algorithm = Algorithm.HMAC256(config.secret)
        accessVerifier = JWT.require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()
    }

    fun verifier(): JWTVerifier = accessVerifier

    fun parseAccessToken(token: String?): AccessTokenClaims? {
        if (token.isNullOrBlank()) return null
        return try {
            val decoded = accessVerifier.verify(token)
            val tokenType = decoded.getClaim("tokenType").asString()
            if (tokenType != "access") return null
            val userId = decoded.getClaim("userId").asString()?.trim().orEmpty()
            val sessionId = decoded.getClaim("sessionId").asString()?.trim().orEmpty()
            if (userId.isBlank() || sessionId.isBlank()) return null
            AccessTokenClaims(userId = userId, sessionId = sessionId)
        } catch (_: Exception) {
            null
        }
    }

    fun createAccessToken(userId: String, sessionId: String): String {
        val expiresAt = Date.from(Instant.now().plus(config.accessTtlMinutes.toLong(), ChronoUnit.MINUTES))
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("userId", userId)
            .withClaim("sessionId", sessionId)
            .withClaim("tokenType", "access")
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }
}

data class AccessTokenClaims(
    val userId: String,
    val sessionId: String,
)
