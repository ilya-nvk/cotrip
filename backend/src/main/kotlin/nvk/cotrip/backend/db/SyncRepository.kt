package nvk.cotrip.backend.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.OffsetDateTime
import java.util.UUID

private val syncJson = Json { ignoreUnknownKeys = true }

data class SyncChangeRow(
    val entity: String,
    val id: String,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    val payload: JsonElement,
)

object SyncRepository {
    fun listChanges(userId: String, since: OffsetDateTime): List<SyncChangeRow> = dbQuery { conn ->
        val changes = mutableListOf<SyncChangeRow>()
        changes += listTripChanges(conn, userId, since)
        changes += listIdeaChanges(conn, userId, since)
        changes += listItineraryDayChanges(conn, userId, since)
        changes += listActivityChanges(conn, userId, since)
        changes += listExpenseChanges(conn, userId, since)
        changes.sortedBy { it.updatedAt }
    }

    private fun listTripChanges(conn: java.sql.Connection, userId: String, since: OffsetDateTime): List<SyncChangeRow> {
        return conn.prepareStatement(
            """
            SELECT t.id, t.updated_at, t.deleted_at,
                   jsonb_build_object(
                       'id', t.id,
                       'ownerId', t.owner_id,
                       'title', t.title,
                       'description', t.description,
                       'startDate', to_jsonb(t.start_date),
                       'endDate', to_jsonb(t.end_date),
                       'locationLine', t.location_line,
                       'coverUrl', t.cover_url,
                       'currencyCode', t.currency_code,
                       'status', t.status,
                       'updatedAt', to_jsonb(t.updated_at)
                   ) AS payload
            FROM trips t
            JOIN trip_members m ON m.trip_id = t.id
            WHERE m.user_id = ? AND m.status = 'accepted'
              AND (t.updated_at >= ? OR t.deleted_at >= ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setObject(2, since)
            stmt.setObject(3, since)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<SyncChangeRow>()
                while (rs.next()) {
                    result += mapChange("trip", rs)
                }
                result
            }
        }
    }

    private fun listIdeaChanges(conn: java.sql.Connection, userId: String, since: OffsetDateTime): List<SyncChangeRow> {
        return conn.prepareStatement(
            """
            SELECT i.id, i.updated_at, i.deleted_at,
                   jsonb_build_object(
                       'id', i.id,
                       'tripId', i.trip_id,
                       'authorId', i.author_id,
                       'title', i.title,
                       'city', i.city,
                       'costAmount', i.cost_amount,
                       'costType', i.cost_type,
                       'website', i.website,
                       'notes', i.notes,
                       'status', i.status,
                       'updatedAt', to_jsonb(i.updated_at)
                   ) AS payload
            FROM ideas i
            JOIN trip_members m ON m.trip_id = i.trip_id
            WHERE m.user_id = ? AND m.status = 'accepted'
              AND (i.updated_at >= ? OR i.deleted_at >= ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setObject(2, since)
            stmt.setObject(3, since)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<SyncChangeRow>()
                while (rs.next()) {
                    result += mapChange("idea", rs)
                }
                result
            }
        }
    }

    private fun listItineraryDayChanges(conn: java.sql.Connection, userId: String, since: OffsetDateTime): List<SyncChangeRow> {
        return conn.prepareStatement(
            """
            SELECT d.id, d.updated_at, NULL::timestamptz AS deleted_at,
                   jsonb_build_object(
                       'id', d.id,
                       'tripId', d.trip_id,
                       'date', to_jsonb(d.date),
                       'dayNumber', d.day_number,
                       'city', d.city,
                       'cityPlaceId', d.city_place_id,
                       'isOutOfRange', d.is_out_of_range,
                       'activities', '[]'::jsonb
                   ) AS payload
            FROM itinerary_days d
            JOIN trip_members m ON m.trip_id = d.trip_id
            WHERE m.user_id = ? AND m.status = 'accepted'
              AND d.updated_at >= ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setObject(2, since)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<SyncChangeRow>()
                while (rs.next()) {
                    result += mapChange("itinerary_day", rs)
                }
                result
            }
        }
    }

    private fun listActivityChanges(conn: java.sql.Connection, userId: String, since: OffsetDateTime): List<SyncChangeRow> {
        return conn.prepareStatement(
            """
            SELECT a.id, a.updated_at, a.deleted_at,
                   jsonb_build_object(
                       'id', a.id,
                       'dayId', a.day_id,
                       'sourceIdeaId', a.source_idea_id,
                       'title', a.title,
                       'timeText', a.time_text,
                       'locationName', a.location_name,
                       'locationLink', a.location_link,
                       'costAmount', a.cost_amount,
                       'costType', a.cost_type,
                       'website', a.website,
                       'notes', a.notes,
                       'orderIndex', a.order_index
                   ) AS payload
            FROM activities a
            JOIN itinerary_days d ON d.id = a.day_id
            JOIN trip_members m ON m.trip_id = d.trip_id
            WHERE m.user_id = ? AND m.status = 'accepted'
              AND (a.updated_at >= ? OR a.deleted_at >= ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setObject(2, since)
            stmt.setObject(3, since)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<SyncChangeRow>()
                while (rs.next()) {
                    result += mapChange("activity", rs)
                }
                result
            }
        }
    }

    private fun listExpenseChanges(conn: java.sql.Connection, userId: String, since: OffsetDateTime): List<SyncChangeRow> {
        return conn.prepareStatement(
            """
            SELECT e.id, e.updated_at, e.deleted_at,
                   jsonb_build_object(
                       'id', e.id,
                       'tripId', e.trip_id,
                       'title', e.title,
                       'amount', e.amount,
                       'currencyCode', e.currency_code,
                       'status', e.status,
                       'paidById', e.paid_by,
                       'date', to_jsonb(e.expense_date),
                       'splitType', e.split_type,
                       'note', e.note,
                       'participants', COALESCE((
                           SELECT jsonb_agg(jsonb_build_object(
                               'userId', s.user_id,
                               'shareAmount', s.share_amount,
                               'isIncluded', s.is_included,
                               'isPaid', s.is_paid
                           ))
                           FROM expense_splits s
                           WHERE s.expense_id = e.id
                       ), '[]'::jsonb)
                   ) AS payload
            FROM expenses e
            JOIN trip_members m ON m.trip_id = e.trip_id
            WHERE m.user_id = ? AND m.status = 'accepted'
              AND (e.updated_at >= ? OR e.deleted_at >= ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(userId))
            stmt.setObject(2, since)
            stmt.setObject(3, since)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<SyncChangeRow>()
                while (rs.next()) {
                    result += mapChange("expense", rs)
                }
                result
            }
        }
    }

    private fun mapChange(entity: String, rs: java.sql.ResultSet): SyncChangeRow {
        val payloadString = rs.getString("payload")
        return SyncChangeRow(
            entity = entity,
            id = rs.getObject("id", UUID::class.java).toString(),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            deletedAt = rs.getObject("deleted_at", OffsetDateTime::class.java),
            payload = syncJson.parseToJsonElement(payloadString),
        )
    }
}
