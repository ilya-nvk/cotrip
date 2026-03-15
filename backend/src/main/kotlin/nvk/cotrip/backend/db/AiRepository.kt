package nvk.cotrip.backend.db

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID

private val json = Json { encodeDefaults = true }

data class AiRequestRow(
    val id: String,
    val tripId: String,
    val city: String?,
    val description: String?,
    val provider: String,
    val createdBy: String,
    val status: String,
    val error: String?,
)

data class AiSuggestionRow(
    val id: String,
    val requestId: String,
    val title: String,
    val place: String?,
    val description: String?,
    val typeLabel: String?,
    val durationLabel: String?,
    val budgetLabel: String?,
    val estimatedCost: Double?,
    val isSaved: Boolean,
    val savedIdeaId: String?,
)

data class AiSuggestionInput(
    val title: String,
    val place: String?,
    val description: String?,
    val typeLabel: String?,
    val durationLabel: String?,
    val budgetLabel: String?,
    val estimatedCost: Double?,
)

data class AiSuggestionWithRequest(
    val suggestion: AiSuggestionRow,
    val tripId: String,
    val city: String?,
    val requestDescription: String?,
)

object AiRepository {
    fun createRequest(
        tripId: String,
        city: String?,
        description: String?,
        typeOptions: List<String>?,
        timeOfDayOptions: List<String>?,
        budgetOptions: List<String>?,
        provider: String,
        createdBy: String,
    ): AiRequestRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO ai_requests (trip_id, city, description, type_options, time_of_day_options, budget_options, provider, created_by, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending')
            RETURNING id, trip_id, city, description, provider, created_by, status, error
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setString(2, city)
            stmt.setString(3, description)
            setJsonb(stmt, 4, typeOptions?.let { json.encodeToString(it) })
            setJsonb(stmt, 5, timeOfDayOptions?.let { json.encodeToString(it) })
            setJsonb(stmt, 6, budgetOptions?.let { json.encodeToString(it) })
            stmt.setString(7, provider)
            stmt.setObject(8, UUID.fromString(createdBy))
            stmt.executeQuery().use { rs ->
                rs.next()
                mapRequest(rs)
            }
        }
    }

    fun updateRequestStatus(requestId: String, status: String, error: String? = null) = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE ai_requests
            SET status = ?, error = ?
            WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, status)
            stmt.setString(2, error)
            stmt.setObject(3, UUID.fromString(requestId))
            stmt.executeUpdate()
        }
    }

    fun insertSuggestions(requestId: String, suggestions: List<AiSuggestionInput>): List<AiSuggestionRow> = dbQuery { conn ->
        if (suggestions.isEmpty()) return@dbQuery emptyList()
        conn.prepareStatement(
            """
            INSERT INTO ai_suggestions (request_id, title, place, description, type_label, duration_label, budget_label, estimated_cost)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            suggestions.forEach { suggestion ->
                stmt.setObject(1, UUID.fromString(requestId))
                stmt.setString(2, suggestion.title)
                stmt.setString(3, suggestion.place)
                stmt.setString(4, suggestion.description)
                stmt.setString(5, suggestion.typeLabel)
                stmt.setString(6, suggestion.durationLabel)
                stmt.setString(7, suggestion.budgetLabel)
                if (suggestion.estimatedCost == null) stmt.setNull(8, java.sql.Types.NUMERIC) else stmt.setDouble(8, suggestion.estimatedCost)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }

        conn.prepareStatement(
            """
            SELECT id, request_id, title, place, description, type_label, duration_label, budget_label, estimated_cost, is_saved, saved_idea_id
            FROM ai_suggestions
            WHERE request_id = ?
            ORDER BY created_at ASC
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(requestId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<AiSuggestionRow>()
                while (rs.next()) {
                    result += mapSuggestion(rs)
                }
                result
            }
        }
    }

    fun getSuggestionWithRequest(suggestionId: String): AiSuggestionWithRequest? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT s.id, s.request_id, s.title, s.place, s.description, s.type_label, s.duration_label, s.budget_label, s.estimated_cost, s.is_saved, s.saved_idea_id,
                   r.trip_id, r.city, r.description AS request_description
            FROM ai_suggestions s
            JOIN ai_requests r ON r.id = s.request_id
            WHERE s.id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(suggestionId))
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@dbQuery null
                val suggestion = mapSuggestion(rs)
                val tripId = rs.getObject("trip_id", UUID::class.java).toString()
                val city = rs.getString("city")
                val requestDescription = rs.getString("request_description")
                AiSuggestionWithRequest(suggestion, tripId, city, requestDescription)
            }
        }
    }

    fun markSuggestionSaved(suggestionId: String, ideaId: String) = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE ai_suggestions
            SET is_saved = true, saved_idea_id = ?
            WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(ideaId))
            stmt.setObject(2, UUID.fromString(suggestionId))
            stmt.executeUpdate()
        }
    }

    private fun mapRequest(rs: ResultSet): AiRequestRow {
        return AiRequestRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            tripId = rs.getObject("trip_id", UUID::class.java).toString(),
            city = rs.getString("city"),
            description = rs.getString("description"),
            provider = rs.getString("provider"),
            createdBy = rs.getObject("created_by", UUID::class.java).toString(),
            status = rs.getString("status"),
            error = rs.getString("error"),
        )
    }

    private fun mapSuggestion(rs: ResultSet): AiSuggestionRow {
        return AiSuggestionRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            requestId = rs.getObject("request_id", UUID::class.java).toString(),
            title = rs.getString("title"),
            place = rs.getString("place"),
            description = rs.getString("description"),
            typeLabel = rs.getString("type_label"),
            durationLabel = rs.getString("duration_label"),
            budgetLabel = rs.getString("budget_label"),
            estimatedCost = rs.getObject("estimated_cost", java.math.BigDecimal::class.java)?.toDouble(),
            isSaved = rs.getBoolean("is_saved"),
            savedIdeaId = rs.getObject("saved_idea_id", UUID::class.java)?.toString(),
        )
    }

    private fun setJsonb(stmt: java.sql.PreparedStatement, index: Int, value: String?) {
        if (value == null) {
            stmt.setNull(index, Types.OTHER)
            return
        }
        val jsonb = PGobject().apply {
            type = "jsonb"
            this.value = value
        }
        stmt.setObject(index, jsonb)
    }
}
