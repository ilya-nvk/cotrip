package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

data class ActivityRow(
    val id: String,
    val dayId: String,
    val sourceIdeaId: String?,
    val title: String,
    val timeText: String?,
    val locationName: String?,
    val link: String?,
    val costAmount: Double?,
    val costType: String?,
    val notes: String?,
    val orderIndex: Int,
    val createdAt: OffsetDateTime,
)

object ActivityRepository {
    fun listByDayIds(dayIds: List<String>): List<ActivityRow> = dbQuery { conn ->
        if (dayIds.isEmpty()) return@dbQuery emptyList<ActivityRow>()
        val placeholders = dayIds.joinToString(",") { "?" }
        val sql = """
            SELECT id, day_id, source_idea_id, title, time_text, location_name, link, cost_amount, cost_type, notes, order_index, created_at
            FROM activities
            WHERE day_id IN ($placeholders) AND deleted_at IS NULL
            ORDER BY day_id, order_index ASC
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            dayIds.forEachIndexed { idx, id ->
                stmt.setObject(idx + 1, UUID.fromString(id))
            }
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ActivityRow>()
                while (rs.next()) {
                    result += mapActivity(rs)
                }
                result
            }
        }
    }

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

    fun create(
        dayId: String,
        sourceIdeaId: String? = null,
        title: String,
        timeText: String?,
        locationName: String?,
        link: String?,
        costAmount: Double?,
        costType: String?,
        notes: String?,
        orderIndex: Int,
    ): ActivityRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO activities (day_id, source_idea_id, title, time_text, location_name, link, cost_amount, cost_type, notes, order_index)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, day_id, source_idea_id, title, time_text, location_name, link, cost_amount, cost_type, notes, order_index, created_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(dayId))
            if (sourceIdeaId == null) stmt.setNull(2, java.sql.Types.OTHER) else stmt.setObject(2, UUID.fromString(sourceIdeaId))
            stmt.setString(3, title)
            stmt.setString(4, timeText)
            stmt.setString(5, locationName)
            stmt.setString(6, link)
            if (costAmount == null) stmt.setNull(7, java.sql.Types.NUMERIC) else stmt.setDouble(7, costAmount)
            stmt.setString(8, costType)
            stmt.setString(9, notes)
            stmt.setInt(10, orderIndex)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapActivity(rs)
            }
        }
    }

    fun createFromIdea(
        dayId: String,
        idea: IdeaRow,
        timeText: String?,
        orderIndex: Int,
    ): ActivityRow = create(
        dayId = dayId,
        sourceIdeaId = idea.id,
        title = idea.title,
        timeText = timeText,
        locationName = idea.city,
        link = idea.link,
        costAmount = idea.costAmount,
        costType = idea.costType,
        notes = idea.notes,
        orderIndex = orderIndex,
    )

    fun get(activityId: String): ActivityRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, day_id, source_idea_id, title, time_text, location_name, link, cost_amount, cost_type, notes, order_index, created_at
            FROM activities
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(activityId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapActivity(rs) else null
            }
        }
    }

    fun update(
        activityId: String,
        title: String?,
        timeText: String?,
        locationName: String?,
        link: String?,
        costAmount: Double?,
        costType: String?,
        notes: String?,
    ): ActivityRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE activities
            SET title = COALESCE(?, title),
                time_text = COALESCE(?, time_text),
                location_name = COALESCE(?, location_name),
                link = COALESCE(?, link),
                cost_amount = COALESCE(?, cost_amount),
                cost_type = COALESCE(?, cost_type),
                notes = COALESCE(?, notes),
                updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            RETURNING id, day_id, source_idea_id, title, time_text, location_name, link, cost_amount, cost_type, notes, order_index, created_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, title)
            stmt.setString(2, timeText)
            stmt.setString(3, locationName)
            stmt.setString(4, link)
            if (costAmount == null) stmt.setNull(5, java.sql.Types.NUMERIC) else stmt.setDouble(5, costAmount)
            stmt.setString(6, costType)
            stmt.setString(7, notes)
            stmt.setObject(8, UUID.fromString(activityId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapActivity(rs) else null
            }
        }
    }

    fun move(
        activityId: String,
        dayId: String,
        orderIndex: Int,
    ): ActivityRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE activities
            SET day_id = ?, order_index = ?, updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            RETURNING id, day_id, source_idea_id, title, time_text, location_name, link, cost_amount, cost_type, notes, order_index, created_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(dayId))
            stmt.setInt(2, orderIndex)
            stmt.setObject(3, UUID.fromString(activityId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapActivity(rs) else null
            }
        }
    }

    fun softDelete(activityId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE activities
            SET deleted_at = now(), updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(activityId))
            stmt.executeUpdate() > 0
        }
    }

    fun reorder(dayId: String, orderedIds: List<String>) = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE activities
            SET order_index = ?
            WHERE id = ? AND day_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            orderedIds.forEachIndexed { index, id ->
                stmt.setInt(1, index)
                stmt.setObject(2, UUID.fromString(id))
                stmt.setObject(3, UUID.fromString(dayId))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun mapActivity(rs: ResultSet): ActivityRow {
        return ActivityRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            dayId = rs.getObject("day_id", UUID::class.java).toString(),
            sourceIdeaId = rs.getObject("source_idea_id", UUID::class.java)?.toString(),
            title = rs.getString("title"),
            timeText = rs.getString("time_text"),
            locationName = rs.getString("location_name"),
            link = rs.getString("link"),
            costAmount = rs.getObject("cost_amount", java.math.BigDecimal::class.java)?.toDouble(),
            costType = rs.getString("cost_type"),
            notes = rs.getString("notes"),
            orderIndex = rs.getInt("order_index"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
        )
    }
}
