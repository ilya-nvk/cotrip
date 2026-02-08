package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID


data class InviteRow(
    val id: String,
    val tripId: String,
    val token: String,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
    val usesCount: Int,
)

object InviteRepository {
    fun revokeActiveInvites(tripId: String) = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE trip_invite_links
            SET revoked_at = now()
            WHERE trip_id = ? AND revoked_at IS NULL AND expires_at > now()
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeUpdate()
        }
    }

    fun createInvite(tripId: String, createdBy: String, token: String, expiresAt: OffsetDateTime): InviteRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO trip_invite_links (trip_id, token, expires_at, created_by)
            VALUES (?, ?, ?, ?)
            RETURNING id, trip_id, token, expires_at, revoked_at, uses_count
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setString(2, token)
            stmt.setObject(3, expiresAt)
            stmt.setObject(4, UUID.fromString(createdBy))
            stmt.executeQuery().use { rs ->
                rs.next()
                mapInvite(rs)
            }
        }
    }

    fun findActiveByToken(token: String): InviteRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, trip_id, token, expires_at, revoked_at, uses_count
            FROM trip_invite_links
            WHERE token = ? AND revoked_at IS NULL AND expires_at > now()
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, token)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapInvite(rs) else null
            }
        }
    }

    fun incrementUse(inviteId: String) = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE trip_invite_links
            SET uses_count = uses_count + 1
            WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(inviteId))
            stmt.executeUpdate()
        }
    }

    private fun mapInvite(rs: ResultSet): InviteRow {
        return InviteRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            tripId = rs.getObject("trip_id", UUID::class.java).toString(),
            token = rs.getString("token"),
            expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
            revokedAt = rs.getObject("revoked_at", OffsetDateTime::class.java),
            usesCount = rs.getInt("uses_count"),
        )
    }
}
