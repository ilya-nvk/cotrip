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

    fun hardDelete(userId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            DELETE FROM users
            WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
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
