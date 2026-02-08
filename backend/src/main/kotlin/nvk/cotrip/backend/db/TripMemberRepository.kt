package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.util.UUID

data class TripMemberRow(
    val userId: String,
    val name: String,
    val photoUrl: String?,
    val role: String,
    val status: String,
)

object TripMemberRepository {
    fun listMembers(tripId: String): List<TripMemberRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT m.user_id, u.name, u.photo_url, m.role, m.status
            FROM trip_members m
            JOIN users u ON u.id = m.user_id
            WHERE m.trip_id = ? AND u.deleted_at IS NULL
            ORDER BY m.joined_at NULLS LAST
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<TripMemberRow>()
                while (rs.next()) {
                    result += mapMember(rs)
                }
                result
            }
        }
    }

    fun findMember(tripId: String, userId: String): TripMemberRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT m.user_id, u.name, u.photo_url, m.role, m.status
            FROM trip_members m
            JOIN users u ON u.id = m.user_id
            WHERE m.trip_id = ? AND m.user_id = ? AND u.deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapMember(rs) else null
            }
        }
    }

    fun removeMember(tripId: String, userId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            DELETE FROM trip_members
            WHERE trip_id = ? AND user_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeUpdate() > 0
        }
    }

    private fun mapMember(rs: ResultSet): TripMemberRow {
        return TripMemberRow(
            userId = rs.getObject("user_id", UUID::class.java).toString(),
            name = rs.getString("name"),
            photoUrl = rs.getString("photo_url"),
            role = rs.getString("role"),
            status = rs.getString("status"),
        )
    }
}
