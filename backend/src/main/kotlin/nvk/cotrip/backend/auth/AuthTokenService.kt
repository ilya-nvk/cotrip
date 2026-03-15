package nvk.cotrip.backend.auth

import nvk.cotrip.backend.config.JwtConfig
import nvk.cotrip.backend.db.UserRepository
import nvk.cotrip.backend.db.dbQuery
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

data class AuthTokenPair(
    val accessToken: String,
    val refreshToken: String,
)

data class AccessIdentity(
    val userId: String,
    val sessionId: String,
)

class AuthFlowException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

object AuthTokenService {
    private lateinit var config: JwtConfig
    private val secureRandom = SecureRandom()

    fun init(config: JwtConfig) {
        this.config = config
    }

    fun issueTokens(userId: String): AuthTokenPair {
        val issued = dbQuery { conn ->
            val now = OffsetDateTime.now()
            val sessionExpiresAt = now.plusDays(config.refreshTtlDays.toLong())
            val sessionId = insertSession(conn, userId, sessionExpiresAt)
            val refreshToken = generateOpaqueToken()
            val refreshTokenHash = hashToken(refreshToken)
            insertRefreshToken(
                conn = conn,
                sessionId = sessionId,
                tokenHash = refreshTokenHash,
                expiresAt = sessionExpiresAt,
            )
            enforceSessionLimit(conn, userId)
            IssuedTokenContext(
                userId = userId,
                sessionId = sessionId.toString(),
                refreshToken = refreshToken,
            )
        }
        return AuthTokenPair(
            accessToken = JwtService.createAccessToken(issued.userId, issued.sessionId),
            refreshToken = issued.refreshToken,
        )
    }

    fun refreshTokens(refreshToken: String): AuthTokenPair {
        val normalizedToken = refreshToken.trim()
        if (normalizedToken.isBlank()) {
            throw AuthFlowException("auth_refresh_invalid", "Invalid refresh token")
        }

        val issued = dbQuery { conn ->
            val lookup = findRefreshForUpdate(conn, hashToken(normalizedToken))
                ?: throw AuthFlowException("auth_refresh_invalid", "Invalid refresh token")

            val now = OffsetDateTime.now()
            if (lookup.sessionRevokedAt != null || lookup.sessionExpiresAt <= now) {
                revokeSession(conn, lookup.sessionId, "session_inactive")
                throw AuthFlowException("auth_refresh_invalid", "Invalid refresh token")
            }
            if (lookup.replacedBy != null) {
                revokeSession(conn, lookup.sessionId, "refresh_reuse")
                throw AuthFlowException("auth_refresh_reuse_detected", "Refresh token reuse detected")
            }
            if (lookup.refreshRevokedAt != null) {
                throw AuthFlowException("auth_refresh_invalid", "Invalid refresh token")
            }
            if (lookup.refreshExpiresAt <= now) {
                revokeSession(conn, lookup.sessionId, "refresh_expired")
                throw AuthFlowException("auth_refresh_invalid", "Invalid refresh token")
            }

            val newSessionExpiry = now.plusDays(config.refreshTtlDays.toLong())
            updateSessionExpiry(conn, lookup.sessionId, newSessionExpiry, now)

            val newRefreshToken = generateOpaqueToken()
            val newRefreshId = insertRefreshToken(
                conn = conn,
                sessionId = lookup.sessionId,
                tokenHash = hashToken(newRefreshToken),
                expiresAt = newSessionExpiry,
            )

            val rotated = markRefreshTokenRotated(
                conn = conn,
                refreshTokenId = lookup.refreshTokenId,
                replacedBy = newRefreshId,
                now = now,
            )
            if (!rotated) {
                revokeSession(conn, lookup.sessionId, "refresh_race")
                throw AuthFlowException("auth_refresh_invalid", "Invalid refresh token")
            }

            IssuedTokenContext(
                userId = lookup.userId,
                sessionId = lookup.sessionId.toString(),
                refreshToken = newRefreshToken,
            )
        }

        return AuthTokenPair(
            accessToken = JwtService.createAccessToken(issued.userId, issued.sessionId),
            refreshToken = issued.refreshToken,
        )
    }

    fun revokeSession(sessionId: String, reason: String = "logout"): Boolean = dbQuery { conn ->
        revokeSession(conn, UUID.fromString(sessionId), reason)
    }

    fun isSessionActiveForUser(sessionId: String, userId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT 1
            FROM auth_sessions
            WHERE id = ?
              AND user_id = ?
              AND revoked_at IS NULL
              AND expires_at > now()
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(sessionId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeQuery().use { rs -> rs.next() }
        }
    }

    fun authenticateAccessToken(token: String?): AccessIdentity? {
        val claims = JwtService.parseAccessToken(token) ?: return null
        val sessionValid = runCatching {
            isSessionActiveForUser(claims.sessionId, claims.userId)
        }.getOrDefault(false)
        if (!sessionValid) return null

        val knownUser = runCatching { UserRepository.findById(claims.userId) != null }
            .getOrDefault(false)
        if (!knownUser) return null

        return AccessIdentity(userId = claims.userId, sessionId = claims.sessionId)
    }

    private fun insertSession(
        conn: Connection,
        userId: String,
        expiresAt: OffsetDateTime,
    ): UUID {
        conn.prepareStatement(
            """
            INSERT INTO auth_sessions (user_id, expires_at)
            VALUES (?, ?)
            RETURNING id
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setObject(2, expiresAt)
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getObject("id", UUID::class.java)
            }
        }
    }

    private fun insertRefreshToken(
        conn: Connection,
        sessionId: UUID,
        tokenHash: String,
        expiresAt: OffsetDateTime,
    ): UUID {
        conn.prepareStatement(
            """
            INSERT INTO auth_refresh_tokens (session_id, token_hash, expires_at)
            VALUES (?, ?, ?)
            RETURNING id
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, sessionId)
            stmt.setString(2, tokenHash)
            stmt.setObject(3, expiresAt)
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getObject("id", UUID::class.java)
            }
        }
    }

    private fun enforceSessionLimit(conn: Connection, userId: String) {
        val idsToRevoke = mutableListOf<UUID>()
        conn.prepareStatement(
            """
            SELECT id
            FROM auth_sessions
            WHERE user_id = ?
              AND revoked_at IS NULL
              AND expires_at > now()
            ORDER BY created_at DESC
            OFFSET ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setInt(2, config.maxActiveSessions)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    idsToRevoke += rs.getObject("id", UUID::class.java)
                }
            }
        }
        idsToRevoke.forEach { sessionId ->
            revokeSession(conn, sessionId, "session_limit")
        }
    }

    private fun revokeSession(
        conn: Connection,
        sessionId: UUID,
        reason: String,
    ): Boolean {
        val sessionUpdated = conn.prepareStatement(
            """
            UPDATE auth_sessions
            SET revoked_at = COALESCE(revoked_at, now()),
                revoke_reason = COALESCE(revoke_reason, ?)
            WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, reason)
            stmt.setObject(2, sessionId)
            stmt.executeUpdate() > 0
        }

        conn.prepareStatement(
            """
            UPDATE auth_refresh_tokens
            SET revoked_at = COALESCE(revoked_at, now())
            WHERE session_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, sessionId)
            stmt.executeUpdate()
        }

        return sessionUpdated
    }

    private fun updateSessionExpiry(
        conn: Connection,
        sessionId: UUID,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ) {
        conn.prepareStatement(
            """
            UPDATE auth_sessions
            SET expires_at = ?,
                last_seen_at = ?
            WHERE id = ? AND revoked_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, expiresAt)
            stmt.setObject(2, now)
            stmt.setObject(3, sessionId)
            stmt.executeUpdate()
        }
    }

    private fun markRefreshTokenRotated(
        conn: Connection,
        refreshTokenId: UUID,
        replacedBy: UUID,
        now: OffsetDateTime,
    ): Boolean {
        return conn.prepareStatement(
            """
            UPDATE auth_refresh_tokens
            SET revoked_at = ?,
                used_at = ?,
                replaced_by = ?
            WHERE id = ?
              AND revoked_at IS NULL
              AND replaced_by IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, now)
            stmt.setObject(2, now)
            stmt.setObject(3, replacedBy)
            stmt.setObject(4, refreshTokenId)
            stmt.executeUpdate() == 1
        }
    }

    private fun findRefreshForUpdate(
        conn: Connection,
        tokenHash: String,
    ): RefreshLookup? {
        conn.prepareStatement(
            """
            SELECT rt.id,
                   rt.session_id,
                   rt.expires_at AS refresh_expires_at,
                   rt.revoked_at AS refresh_revoked_at,
                   rt.replaced_by,
                   s.user_id,
                   s.expires_at AS session_expires_at,
                   s.revoked_at AS session_revoked_at
            FROM auth_refresh_tokens rt
            JOIN auth_sessions s ON s.id = rt.session_id
            WHERE rt.token_hash = ?
            FOR UPDATE
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, tokenHash)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return RefreshLookup(
                    refreshTokenId = rs.getObject("id", UUID::class.java),
                    sessionId = rs.getObject("session_id", UUID::class.java),
                    userId = rs.getObject("user_id", UUID::class.java).toString(),
                    refreshExpiresAt = rs.getObject("refresh_expires_at", OffsetDateTime::class.java),
                    refreshRevokedAt = rs.getObject("refresh_revoked_at", OffsetDateTime::class.java),
                    replacedBy = rs.getObject("replaced_by", UUID::class.java),
                    sessionExpiresAt = rs.getObject("session_expires_at", OffsetDateTime::class.java),
                    sessionRevokedAt = rs.getObject("session_revoked_at", OffsetDateTime::class.java),
                )
            }
        }
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private data class RefreshLookup(
        val refreshTokenId: UUID,
        val sessionId: UUID,
        val userId: String,
        val refreshExpiresAt: OffsetDateTime,
        val refreshRevokedAt: OffsetDateTime?,
        val replacedBy: UUID?,
        val sessionExpiresAt: OffsetDateTime,
        val sessionRevokedAt: OffsetDateTime?,
    )

    private data class IssuedTokenContext(
        val userId: String,
        val sessionId: String,
        val refreshToken: String,
    )
}
