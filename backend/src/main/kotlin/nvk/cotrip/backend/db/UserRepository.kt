package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID


data class UserRow(
    val id: String,
    val googleId: String,
    val name: String,
    val photoUrl: String?,
    val deletedAt: OffsetDateTime?,
)

object UserRepository {
    fun findByGoogleId(googleId: String): UserRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, google_id, name, photo_url, deleted_at
            FROM users
            WHERE google_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, googleId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapUser(rs) else null
            }
        }
    }

    fun findByGoogleIdAny(googleId: String): UserRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, google_id, name, photo_url, deleted_at
            FROM users
            WHERE google_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, googleId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapUser(rs) else null
            }
        }
    }

    fun findById(userId: String): UserRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, google_id, name, photo_url, deleted_at
            FROM users
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapUser(rs) else null
            }
        }
    }

    fun createUser(googleId: String, name: String, photoUrl: String?): UserRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO users (google_id, name, photo_url)
            VALUES (?, ?, ?)
            RETURNING id, google_id, name, photo_url, deleted_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, googleId)
            stmt.setString(2, name)
            stmt.setString(3, photoUrl)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapUser(rs)
            }
        }
    }

    fun updateUser(userId: String, name: String, photoUrl: String?): UserRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE users
            SET name = ?, photo_url = ?
            WHERE id = ? AND deleted_at IS NULL
            RETURNING id, google_id, name, photo_url, deleted_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, photoUrl)
            stmt.setObject(3, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapUser(rs) else null
            }
        }
    }

    fun restoreUser(userId: String, name: String, photoUrl: String?): UserRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE users
            SET name = ?, photo_url = ?, deleted_at = NULL
            WHERE id = ?
            RETURNING id, google_id, name, photo_url, deleted_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, photoUrl)
            stmt.setObject(3, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapUser(rs) else null
            }
        }
    }

    fun softDelete(userId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE users
            SET deleted_at = now()
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.executeUpdate() > 0
        }
    }

    fun deleteUserAndData(userId: String): Boolean = dbQuery { conn ->
        val id = UUID.fromString(userId)

        fun exec(sql: String, times: Int = 1) {
            conn.prepareStatement(sql).use { stmt ->
                repeat(times) { index -> stmt.setObject(index + 1, id) }
                stmt.executeUpdate()
            }
        }

        // User-scoped data (any trips).
        exec("DELETE FROM notifications WHERE user_id = ?")
        exec("DELETE FROM notification_settings WHERE user_id = ?")
        exec("DELETE FROM push_tokens WHERE user_id = ?")
        exec("DELETE FROM trip_invite_links WHERE created_by = ?")
        exec("DELETE FROM trip_members WHERE user_id = ?")
        exec("DELETE FROM expense_splits WHERE user_id = ?")
        exec("UPDATE expenses SET paid_by = NULL WHERE paid_by = ?")
        exec("DELETE FROM idea_comments WHERE author_id = ?")

        // Ideas authored by the user in other trips.
        exec(
            """
            DELETE FROM ai_suggestions
            WHERE saved_idea_id IN (SELECT id FROM ideas WHERE author_id = ?)
            """.trimIndent()
        )
        exec(
            """
            UPDATE activities
            SET source_idea_id = NULL
            WHERE source_idea_id IN (SELECT id FROM ideas WHERE author_id = ?)
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM idea_comments
            WHERE idea_id IN (SELECT id FROM ideas WHERE author_id = ?)
            """.trimIndent()
        )
        exec("DELETE FROM ideas WHERE author_id = ?")

        // Trips owned by the user + all trip data.
        exec(
            """
            DELETE FROM ai_suggestions
            WHERE request_id IN (
                SELECT id FROM ai_requests WHERE trip_id IN (
                    SELECT id FROM trips WHERE owner_id = ?
                )
            )
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM ai_suggestions
            WHERE saved_idea_id IN (
                SELECT id FROM ideas WHERE trip_id IN (
                    SELECT id FROM trips WHERE owner_id = ?
                )
            )
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM ai_requests
            WHERE trip_id IN (SELECT id FROM trips WHERE owner_id = ?)
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM weather_forecasts
            WHERE trip_id IN (SELECT id FROM trips WHERE owner_id = ?)
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM expense_splits
            WHERE expense_id IN (
                SELECT id FROM expenses WHERE trip_id IN (
                    SELECT id FROM trips WHERE owner_id = ?
                )
            )
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM expenses
            WHERE trip_id IN (SELECT id FROM trips WHERE owner_id = ?)
            """.trimIndent()
        )
        exec(
            """
            UPDATE activities
            SET source_idea_id = NULL
            WHERE source_idea_id IN (
                SELECT id FROM ideas WHERE trip_id IN (
                    SELECT id FROM trips WHERE owner_id = ?
                )
            )
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM activities
            WHERE day_id IN (
                SELECT id FROM itinerary_days WHERE trip_id IN (
                    SELECT id FROM trips WHERE owner_id = ?
                )
            )
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM itinerary_days
            WHERE trip_id IN (SELECT id FROM trips WHERE owner_id = ?)
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM idea_comments
            WHERE idea_id IN (
                SELECT id FROM ideas WHERE trip_id IN (
                    SELECT id FROM trips WHERE owner_id = ?
                )
            )
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM ideas
            WHERE trip_id IN (SELECT id FROM trips WHERE owner_id = ?)
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM trip_invite_links
            WHERE trip_id IN (SELECT id FROM trips WHERE owner_id = ?)
            """.trimIndent()
        )
        exec(
            """
            DELETE FROM trip_members
            WHERE trip_id IN (SELECT id FROM trips WHERE owner_id = ?)
            """.trimIndent()
        )
        exec("DELETE FROM trips WHERE owner_id = ?")

        conn.prepareStatement(
            """
            DELETE FROM users
            WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.executeUpdate() > 0
        }
    }

    private fun mapUser(rs: ResultSet): UserRow {
        return UserRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            googleId = rs.getString("google_id"),
            name = rs.getString("name"),
            photoUrl = rs.getString("photo_url"),
            deletedAt = rs.getObject("deleted_at", OffsetDateTime::class.java),
        )
    }
}
