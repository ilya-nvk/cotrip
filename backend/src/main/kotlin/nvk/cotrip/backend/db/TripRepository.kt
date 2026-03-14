package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class TripRow(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val locationLine: String?,
    val coverUrl: String?,
    val currencyCode: String,
    val status: String,
    val updatedAt: OffsetDateTime,
)

object TripRepository {
    fun createTrip(
        ownerId: String,
        title: String,
        description: String?,
        startDate: LocalDate,
        endDate: LocalDate,
        locationLine: String?,
        coverUrl: String?,
        currencyCode: String,
    ): TripRow = dbQuery { conn ->
        val trip = conn.prepareStatement(
            """
            INSERT INTO trips (owner_id, title, description, start_date, end_date, location_line, cover_url, currency_code, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active')
            RETURNING id, owner_id, title, description, start_date, end_date, location_line, cover_url, currency_code, status, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(ownerId))
            stmt.setString(2, title)
            stmt.setString(3, description)
            stmt.setObject(4, startDate)
            stmt.setObject(5, endDate)
            stmt.setString(6, locationLine)
            stmt.setString(7, coverUrl)
            stmt.setString(8, currencyCode)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapTrip(rs)
            }
        }

        conn.prepareStatement(
            """
            INSERT INTO trip_members (trip_id, user_id, role, status, joined_at)
            VALUES (?, ?, 'owner', 'accepted', now())
            ON CONFLICT (trip_id, user_id) DO UPDATE SET role = 'owner', status = 'accepted', joined_at = now()
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(trip.id))
            stmt.setObject(2, UUID.fromString(ownerId))
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            """
            INSERT INTO itinerary_days (trip_id, date, day_number)
            SELECT ?, day::date, ROW_NUMBER() OVER (ORDER BY day)
            FROM generate_series(?, ?, interval '1 day') AS day
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(trip.id))
            stmt.setObject(2, trip.startDate)
            stmt.setObject(3, trip.endDate)
            stmt.executeUpdate()
        }

        trip
    }

    fun listTripsForUser(userId: String, status: String?): List<TripRow> = dbQuery { conn ->
        val today = LocalDate.now()
        val conditions = mutableListOf<String>()
        conditions += "m.user_id = ?"
        conditions += "m.status = 'accepted'"
        conditions += "t.deleted_at IS NULL"

        when (status) {
            "archived" -> conditions += "t.status = 'archived'"
            "active" -> {
                conditions += "t.status = 'active'"
                conditions += "t.start_date <= ? AND t.end_date >= ?"
            }
            "upcoming" -> {
                conditions += "t.status = 'active'"
                conditions += "t.start_date > ?"
            }
            "past" -> {
                conditions += "t.status = 'active'"
                conditions += "t.end_date < ?"
            }
        }

        val sql = """
            SELECT t.id, t.owner_id, t.title, t.description, t.start_date, t.end_date, t.location_line, t.cover_url, t.currency_code, t.status, t.updated_at
            FROM trips t
            JOIN trip_members m ON m.trip_id = t.id
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY t.start_date ASC
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            var index = 1
            stmt.setObject(index++, UUID.fromString(userId))
            when (status) {
                "active" -> {
                    stmt.setObject(index++, today)
                    stmt.setObject(index++, today)
                }
                "upcoming" -> stmt.setObject(index++, today)
                "past" -> stmt.setObject(index++, today)
            }

            stmt.executeQuery().use { rs ->
                val result = mutableListOf<TripRow>()
                while (rs.next()) {
                    result += mapTrip(rs)
                }
                result
            }
        }
    }

    fun getTripForUser(userId: String, tripId: String): TripRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT t.id, t.owner_id, t.title, t.description, t.start_date, t.end_date, t.location_line, t.cover_url, t.currency_code, t.status, t.updated_at
            FROM trips t
            JOIN trip_members m ON m.trip_id = t.id
            WHERE t.id = ? AND m.user_id = ? AND m.status = 'accepted' AND t.deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapTrip(rs) else null
            }
        }
    }

    fun getTripById(tripId: String): TripRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, owner_id, title, description, start_date, end_date, location_line, cover_url, currency_code, status, updated_at
            FROM trips
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapTrip(rs) else null
            }
        }
    }

    fun updateTrip(ownerId: String, tripId: String, update: TripUpdate): TripRow? = dbQuery { conn ->
        val existing = conn.prepareStatement(
            """
            SELECT id, owner_id, title, description, start_date, end_date, location_line, cover_url, currency_code, status, updated_at
            FROM trips
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(ownerId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapTrip(rs) else null
            }
        } ?: return@dbQuery null

        val updatedTrip = conn.prepareStatement(
            """
            UPDATE trips
            SET title = ?, description = ?, start_date = ?, end_date = ?, location_line = ?, cover_url = ?, currency_code = ?, updated_at = now()
            WHERE id = ? AND owner_id = ?
            RETURNING id, owner_id, title, description, start_date, end_date, location_line, cover_url, currency_code, status, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, update.title ?: existing.title)
            stmt.setString(2, update.description ?: existing.description)
            stmt.setObject(3, update.startDate ?: existing.startDate)
            stmt.setObject(4, update.endDate ?: existing.endDate)
            stmt.setString(5, update.locationLine ?: existing.locationLine)
            stmt.setString(6, update.coverUrl ?: existing.coverUrl)
            stmt.setString(7, update.currencyCode ?: existing.currencyCode)
            stmt.setObject(8, UUID.fromString(tripId))
            stmt.setObject(9, UUID.fromString(ownerId))
            stmt.executeQuery().use { rs ->
                rs.next()
                mapTrip(rs)
            }
        }

        val oldDurationDays = inclusiveDurationDays(existing.startDate, existing.endDate)
        val newDurationDays = inclusiveDurationDays(updatedTrip.startDate, updatedTrip.endDate)
        if (oldDurationDays == newDurationDays) {
            val shiftDays = ChronoUnit.DAYS.between(existing.startDate, updatedTrip.startDate)
            shiftItineraryDays(
                conn = conn,
                tripId = updatedTrip.id,
                shiftDays = shiftDays,
            )
        }

        reconcileItineraryDays(
            conn = conn,
            tripId = updatedTrip.id,
            startDate = updatedTrip.startDate,
            endDate = updatedTrip.endDate,
        )

        updatedTrip
    }

    fun extendTripEndByOutOfRangeDays(
        ownerId: String,
        tripId: String,
        dayIds: List<String>,
    ): TripRow? = dbQuery { conn ->
        val trip = conn.prepareStatement(
            """
            SELECT id, owner_id, title, description, start_date, end_date, location_line, cover_url, currency_code, status, updated_at
            FROM trips
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(ownerId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapTrip(rs) else null
            }
        } ?: return@dbQuery null

        if (dayIds.isEmpty()) return@dbQuery trip

        val placeholders = dayIds.joinToString(",") { "?" }
        val outOfRangeDays = conn.prepareStatement(
            """
            SELECT id, date
            FROM itinerary_days
            WHERE trip_id = ?
              AND id IN ($placeholders)
              AND is_out_of_range = true
            ORDER BY date ASC, id ASC
            """.trimIndent()
        ).use { stmt ->
            var index = 1
            stmt.setObject(index++, UUID.fromString(tripId))
            dayIds.forEach { id ->
                stmt.setObject(index++, UUID.fromString(id))
            }
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<String>()
                while (rs.next()) {
                    result += rs.getObject("id", UUID::class.java).toString()
                }
                result
            }
        }

        if (outOfRangeDays.isEmpty()) return@dbQuery trip

        var nextDate = trip.endDate
        conn.prepareStatement(
            """
            UPDATE itinerary_days
            SET date = ?, is_out_of_range = false, updated_at = now()
            WHERE id = ? AND trip_id = ?
            """.trimIndent()
        ).use { stmt ->
            outOfRangeDays.forEach { dayId ->
                nextDate = nextDate.plusDays(1)
                stmt.setObject(1, nextDate)
                stmt.setObject(2, UUID.fromString(dayId))
                stmt.setObject(3, UUID.fromString(tripId))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }

        val updatedTrip = conn.prepareStatement(
            """
            UPDATE trips
            SET end_date = ?, updated_at = now()
            WHERE id = ? AND owner_id = ?
            RETURNING id, owner_id, title, description, start_date, end_date, location_line, cover_url, currency_code, status, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, nextDate)
            stmt.setObject(2, UUID.fromString(tripId))
            stmt.setObject(3, UUID.fromString(ownerId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapTrip(rs) else null
            }
        } ?: return@dbQuery null

        reconcileItineraryDays(
            conn = conn,
            tripId = updatedTrip.id,
            startDate = updatedTrip.startDate,
            endDate = updatedTrip.endDate,
        )

        updatedTrip
    }

    private fun reconcileItineraryDays(
        conn: Connection,
        tripId: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ) {
        conn.prepareStatement(
            """
            INSERT INTO itinerary_days (trip_id, date, day_number, is_out_of_range)
            SELECT ?, day::date, 0, false
            FROM generate_series(?::date, ?::date, interval '1 day') AS day
            WHERE NOT EXISTS (
                SELECT 1
                FROM itinerary_days d
                WHERE d.trip_id = ? AND d.date = day::date
            )
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, startDate)
            stmt.setObject(3, endDate)
            stmt.setObject(4, UUID.fromString(tripId))
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            """
            UPDATE itinerary_days
            SET is_out_of_range = (date < ? OR date > ?),
                updated_at = now()
            WHERE trip_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, startDate)
            stmt.setObject(2, endDate)
            stmt.setObject(3, UUID.fromString(tripId))
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            """
            WITH ranked AS (
                SELECT id, ROW_NUMBER() OVER (ORDER BY date ASC) AS rn
                FROM itinerary_days
                WHERE trip_id = ?
            )
            UPDATE itinerary_days d
            SET day_number = ranked.rn,
                updated_at = now()
            FROM ranked
            WHERE d.id = ranked.id
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeUpdate()
        }
    }

    private fun shiftItineraryDays(
        conn: Connection,
        tripId: String,
        shiftDays: Long,
    ) {
        if (shiftDays == 0L) return
        conn.prepareStatement(
            """
            UPDATE itinerary_days
            SET date = date + (?::int),
                updated_at = now()
            WHERE trip_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setInt(1, shiftDays.toInt())
            stmt.setObject(2, UUID.fromString(tripId))
            stmt.executeUpdate()
        }
    }

    fun archiveTrip(ownerId: String, tripId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE trips
            SET status = 'archived', updated_at = now()
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(ownerId))
            stmt.executeUpdate() > 0
        }
    }

    fun deleteTrip(ownerId: String, tripId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE trips
            SET deleted_at = now(), updated_at = now()
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(ownerId))
            stmt.executeUpdate() > 0
        }
    }

    fun transferOwner(ownerId: String, tripId: String, newOwnerId: String): Boolean = dbQuery { conn ->
        val memberCheck = conn.prepareStatement(
            """
            SELECT status FROM trip_members
            WHERE trip_id = ? AND user_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(newOwnerId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getString("status") else null
            }
        }

        if (memberCheck != "accepted") return@dbQuery false

        val updated = conn.prepareStatement(
            """
            UPDATE trips
            SET owner_id = ?, updated_at = now()
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(newOwnerId))
            stmt.setObject(2, UUID.fromString(tripId))
            stmt.setObject(3, UUID.fromString(ownerId))
            stmt.executeUpdate() > 0
        }

        if (!updated) return@dbQuery false

        conn.prepareStatement(
            """
            UPDATE trip_members
            SET role = 'member'
            WHERE trip_id = ? AND user_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(ownerId))
            stmt.executeUpdate()
        }

        conn.prepareStatement(
            """
            UPDATE trip_members
            SET role = 'owner'
            WHERE trip_id = ? AND user_id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(newOwnerId))
            stmt.executeUpdate()
        }

        true
    }

    fun upsertMemberAccepted(tripId: String, userId: String, role: String) = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO trip_members (trip_id, user_id, role, status, joined_at)
            VALUES (?, ?, ?, 'accepted', now())
            ON CONFLICT (trip_id, user_id)
            DO UPDATE SET status = 'accepted', role = EXCLUDED.role, joined_at = now()
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.setString(3, role)
            stmt.executeUpdate()
        }
    }

    fun isOwner(tripId: String, userId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT 1 FROM trips
            WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                rs.next()
            }
        }
    }

    fun isMember(tripId: String, userId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT 1 FROM trip_members
            WHERE trip_id = ? AND user_id = ? AND status = 'accepted'
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(userId))
            stmt.executeQuery().use { rs ->
                rs.next()
            }
        }
    }

    private fun mapTrip(rs: ResultSet): TripRow {
        return TripRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            ownerId = rs.getObject("owner_id", UUID::class.java).toString(),
            title = rs.getString("title"),
            description = rs.getString("description"),
            startDate = rs.getObject("start_date", LocalDate::class.java),
            endDate = rs.getObject("end_date", LocalDate::class.java),
            locationLine = rs.getString("location_line"),
            coverUrl = rs.getString("cover_url"),
            currencyCode = rs.getString("currency_code"),
            status = rs.getString("status"),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }
}

private fun inclusiveDurationDays(start: LocalDate, end: LocalDate): Long {
    return ChronoUnit.DAYS.between(start, end) + 1
}

data class TripUpdate(
    val title: String?,
    val description: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val locationLine: String?,
    val coverUrl: String?,
    val currencyCode: String?,
)
