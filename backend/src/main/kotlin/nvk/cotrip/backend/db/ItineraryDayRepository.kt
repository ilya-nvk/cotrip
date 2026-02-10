package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

data class ItineraryDayRow(
    val id: String,
    val tripId: String,
    val date: LocalDate,
    val dayNumber: Int,
    val city: String?,
    val cityPlaceId: String?,
    val isOutOfRange: Boolean,
)

object ItineraryDayRepository {
    fun listByTrip(tripId: String): List<ItineraryDayRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, trip_id, date, day_number, city, city_place_id, is_out_of_range
            FROM itinerary_days
            WHERE trip_id = ?
            ORDER BY date ASC
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ItineraryDayRow>()
                while (rs.next()) {
                    result += mapDay(rs)
                }
                result
            }
        }
    }

    fun updateCity(dayId: String, city: String?, cityPlaceId: String?): ItineraryDayRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE itinerary_days
            SET city = ?, city_place_id = ?, updated_at = now()
            WHERE id = ?
            RETURNING id, trip_id, date, day_number, city, city_place_id, is_out_of_range
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, city)
            stmt.setString(2, cityPlaceId)
            stmt.setObject(3, UUID.fromString(dayId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapDay(rs) else null
            }
        }
    }

    fun markOutOfRange(dayIds: List<String>, value: Boolean) = dbQuery { conn ->
        if (dayIds.isEmpty()) return@dbQuery
        val placeholders = dayIds.joinToString(",") { "?" }
        val sql = """
            UPDATE itinerary_days
            SET is_out_of_range = ?
            WHERE id IN ($placeholders)
            """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setBoolean(1, value)
            dayIds.forEachIndexed { idx, id ->
                stmt.setObject(idx + 2, UUID.fromString(id))
            }
            stmt.executeUpdate()
        }
    }

    fun deleteDays(dayIds: List<String>) = dbQuery { conn ->
        if (dayIds.isEmpty()) return@dbQuery
        val placeholders = dayIds.joinToString(",") { "?" }
        conn.prepareStatement(
            """
            DELETE FROM activities
            WHERE day_id IN ($placeholders)
            """.trimIndent()
        ).use { stmt ->
            dayIds.forEachIndexed { idx, id ->
                stmt.setObject(idx + 1, UUID.fromString(id))
            }
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            """
            DELETE FROM itinerary_days
            WHERE id IN ($placeholders)
            """.trimIndent()
        ).use { stmt ->
            dayIds.forEachIndexed { idx, id ->
                stmt.setObject(idx + 1, UUID.fromString(id))
            }
            stmt.executeUpdate()
        }
    }

    private fun mapDay(rs: ResultSet): ItineraryDayRow {
        return ItineraryDayRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            tripId = rs.getObject("trip_id", UUID::class.java).toString(),
            date = rs.getObject("date", LocalDate::class.java),
            dayNumber = rs.getInt("day_number"),
            city = rs.getString("city"),
            cityPlaceId = rs.getString("city_place_id"),
            isOutOfRange = rs.getBoolean("is_out_of_range"),
        )
    }
}
