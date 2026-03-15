package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

data class NotificationRow(
    val id: String,
    val userId: String,
    val type: String,
    val payload: String,
    val createdAt: OffsetDateTime,
    val readAt: OffsetDateTime?,
)

data class NotificationSettingRow(
    val userId: String,
    val key: String,
    val enabled: Boolean,
)

object NotificationRepository {
    fun create(userId: String, type: String, payload: String): NotificationRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO notifications (user_id, type, payload)
            VALUES (?, ?, ?::jsonb)
            RETURNING id, user_id, type, payload, created_at, read_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setString(2, type)
            stmt.setString(3, payload)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapNotification(rs)
            }
        }
    }

    fun isSettingEnabled(userId: String, key: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT enabled
            FROM notification_settings
            WHERE user_id = ? AND key = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setString(2, key)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getBoolean("enabled") else true
            }
        }
    }

    fun listForUser(userId: String, limit: Int = 100): List<NotificationRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, user_id, type, payload, created_at, read_at
            FROM notifications
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<NotificationRow>()
                while (rs.next()) {
                    result += mapNotification(rs)
                }
                result
            }
        }
    }

    fun markRead(userId: String, notificationId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE notifications
            SET read_at = now()
            WHERE id = ? AND user_id = ? AND read_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(notificationId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeUpdate() > 0
        }
    }

    fun markReadBulkNonComment(userId: String): Int = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE notifications
            SET read_at = now()
            WHERE user_id = ? AND read_at IS NULL AND type <> 'idea_comment'
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.executeUpdate()
        }
    }

    fun markReadBulkIdeaComments(userId: String, ideaId: String): Int = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE notifications
            SET read_at = now()
            WHERE user_id = ?
              AND read_at IS NULL
              AND type = 'idea_comment'
              AND payload ->> 'ideaId' = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setString(2, ideaId)
            stmt.executeUpdate()
        }
    }

    fun listSettings(userId: String): List<NotificationSettingRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT user_id, key, enabled
            FROM notification_settings
            WHERE user_id = ?
            ORDER BY key ASC
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<NotificationSettingRow>()
                while (rs.next()) {
                    result += mapSetting(rs)
                }
                result
            }
        }
    }

    fun upsertSettings(userId: String, items: List<NotificationSettingRow>) = dbQuery { conn ->
        if (items.isEmpty()) return@dbQuery
        conn.prepareStatement(
            """
            INSERT INTO notification_settings (user_id, key, enabled)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id, key) DO UPDATE SET enabled = EXCLUDED.enabled
            """.trimIndent()
        ).use { stmt ->
            items.forEach { item ->
                stmt.setObject(1, UUID.fromString(userId))
                stmt.setString(2, item.key)
                stmt.setBoolean(3, item.enabled)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun mapNotification(rs: ResultSet): NotificationRow {
        return NotificationRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            userId = rs.getObject("user_id", UUID::class.java).toString(),
            type = rs.getString("type"),
            payload = rs.getString("payload"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            readAt = rs.getObject("read_at", OffsetDateTime::class.java),
        )
    }

    private fun mapSetting(rs: ResultSet): NotificationSettingRow {
        return NotificationSettingRow(
            userId = rs.getObject("user_id", UUID::class.java).toString(),
            key = rs.getString("key"),
            enabled = rs.getBoolean("enabled"),
        )
    }
}
