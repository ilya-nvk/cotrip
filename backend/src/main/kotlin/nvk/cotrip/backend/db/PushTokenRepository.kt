package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

data class PushTokenRow(
    val token: String,
    val userId: String,
    val platform: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

object PushTokenRepository {
    fun upsert(token: String, userId: String, platform: String) = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO push_tokens (token, user_id, platform)
            VALUES (?, ?, ?)
            ON CONFLICT (token) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                platform = EXCLUDED.platform,
                updated_at = now()
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, token)
            stmt.setObject(2, UUID.fromString(userId))
            stmt.setString(3, platform)
            stmt.executeUpdate()
        }
    }

    fun removeForUser(token: String, userId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            DELETE FROM push_tokens
            WHERE token = ? AND user_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, token)
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeUpdate() > 0
        }
    }

    fun removeByToken(token: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            DELETE FROM push_tokens
            WHERE token = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, token)
            stmt.executeUpdate() > 0
        }
    }

    fun listByUserId(userId: String): List<PushTokenRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT token, user_id, platform, created_at, updated_at
            FROM push_tokens
            WHERE user_id = ?
            ORDER BY updated_at DESC
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<PushTokenRow>()
                while (rs.next()) {
                    result += mapRow(rs)
                }
                result
            }
        }
    }

    private fun mapRow(rs: ResultSet): PushTokenRow {
        return PushTokenRow(
            token = rs.getString("token"),
            userId = rs.getObject("user_id", UUID::class.java).toString(),
            platform = rs.getString("platform"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }
}
