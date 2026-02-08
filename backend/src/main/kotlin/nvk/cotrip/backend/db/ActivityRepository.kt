package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

data class ActivityRow(
    val id: String,
    val dayId: String,
    val title: String,
    val timeText: String?,
    val orderIndex: Int,
    val createdAt: OffsetDateTime,
)

object ActivityRepository {
    fun nextOrderIndex(dayId: String): Int = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT COALESCE(MAX(order_index), -1) AS max_order
            FROM activities
            WHERE day_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(dayId))
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt("max_order") + 1
            }
        }
    }

    fun createFromIdea(
        dayId: String,
        idea: IdeaRow,
        timeText: String?,
        orderIndex: Int,
    ): ActivityRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO activities (day_id, source_idea_id, title, time_text, location_name, location_link, cost_amount, cost_type, website, notes, order_index)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, day_id, title, time_text, order_index, created_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(dayId))
            stmt.setObject(2, UUID.fromString(idea.id))
            stmt.setString(3, idea.title)
            stmt.setString(4, timeText)
            stmt.setString(5, idea.city)
            stmt.setString(6, idea.website)
            if (idea.costAmount == null) stmt.setNull(7, java.sql.Types.NUMERIC) else stmt.setDouble(7, idea.costAmount)
            stmt.setString(8, idea.costType)
            stmt.setString(9, idea.website)
            stmt.setString(10, idea.notes)
            stmt.setInt(11, orderIndex)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapActivity(rs)
            }
        }
    }

    private fun mapActivity(rs: ResultSet): ActivityRow {
        return ActivityRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            dayId = rs.getObject("day_id", UUID::class.java).toString(),
            title = rs.getString("title"),
            timeText = rs.getString("time_text"),
            orderIndex = rs.getInt("order_index"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
        )
    }
}
