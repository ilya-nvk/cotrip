package nvk.cotrip.backend.db

import java.util.UUID

data class PushTokenRow(
    val token: String,
    val userId: String,
)

object PushTokenRepository {
    fun upsert(userId: String, token: String, platform: String): Unit = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO push_tokens (token, user_id, platform)
            VALUES (?, ?, ?)
            ON CONFLICT (token)
            DO UPDATE SET
              user_id = EXCLUDED.user_id,
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

    fun deleteByToken(token: String): Boolean = dbQuery { conn ->
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

    fun deleteByUserAndToken(userId: String, token: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            DELETE FROM push_tokens
            WHERE user_id = ? AND token = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setString(2, token)
            stmt.executeUpdate() > 0
        }
    }

    fun listByUserIds(userIds: Collection<String>): List<PushTokenRow> = dbQuery { conn ->
        if (userIds.isEmpty()) return@dbQuery emptyList()
        val ids = userIds.map { UUID.fromString(it) }
        conn.prepareStatement(
            """
            SELECT token, user_id
            FROM push_tokens
            WHERE user_id = ANY (?)
            ORDER BY updated_at DESC
            """.trimIndent()
        ).use { stmt ->
            stmt.setArray(1, conn.createArrayOf("uuid", ids.toTypedArray()))
            stmt.executeQuery().use { rs ->
                val rows = mutableListOf<PushTokenRow>()
                while (rs.next()) {
                    rows += PushTokenRow(
                        token = rs.getString("token"),
                        userId = rs.getObject("user_id", UUID::class.java).toString()
                    )
                }
                rows
            }
        }
    }
}
