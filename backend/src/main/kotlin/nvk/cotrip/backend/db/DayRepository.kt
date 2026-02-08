package nvk.cotrip.backend.db

import java.util.UUID

object DayRepository {
    fun findTripIdByDayId(dayId: String): String? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT trip_id
            FROM itinerary_days
            WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(dayId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getObject("trip_id", UUID::class.java).toString() else null
            }
        }
    }
}
