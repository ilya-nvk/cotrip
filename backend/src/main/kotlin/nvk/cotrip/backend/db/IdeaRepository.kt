package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID


data class IdeaRow(
    val id: String,
    val tripId: String,
    val authorId: String,
    val title: String,
    val city: String?,
    val link: String?,
    val costAmount: Double?,
    val costType: String?,
    val website: String?,
    val notes: String?,
    val status: String,
    val updatedAt: OffsetDateTime,
)

object IdeaRepository {
    fun findTripIdByIdeaId(ideaId: String): String? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT trip_id
            FROM ideas
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(ideaId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getObject("trip_id", UUID::class.java).toString() else null
            }
        }
    }

    fun list(
        tripId: String,
        search: String?,
        status: String?,
        authorId: String?,
        city: String?,
    ): List<IdeaRow> = dbQuery { conn ->
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()
        conditions += "trip_id = ?"
        params += UUID.fromString(tripId)
        conditions += "deleted_at IS NULL"

        if (!search.isNullOrBlank()) {
            conditions += "title ILIKE ?"
            params += "%${search.trim()}%"
        }
        if (!status.isNullOrBlank()) {
            conditions += "status = ?"
            params += status
        }
        if (!authorId.isNullOrBlank()) {
            conditions += "author_id = ?"
            params += UUID.fromString(authorId)
        }
        if (!city.isNullOrBlank()) {
            conditions += "city ILIKE ?"
            params += city.trim()
        }

        val sql = """
            SELECT id, trip_id, author_id, title, city, link, cost_amount, cost_type, website, notes, status, updated_at
            FROM ideas
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY updated_at DESC
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { idx, value ->
                when (value) {
                    is UUID -> stmt.setObject(idx + 1, value)
                    is String -> stmt.setString(idx + 1, value)
                    else -> stmt.setObject(idx + 1, value)
                }
            }

            stmt.executeQuery().use { rs ->
                val result = mutableListOf<IdeaRow>()
                while (rs.next()) {
                    result += mapIdea(rs)
                }
                result
            }
        }
    }

    fun get(ideaId: String): IdeaRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, trip_id, author_id, title, city, link, cost_amount, cost_type, website, notes, status, updated_at
            FROM ideas
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(ideaId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapIdea(rs) else null
            }
        }
    }

    fun create(
        tripId: String,
        authorId: String,
        title: String,
        city: String?,
        link: String?,
        costAmount: Double?,
        costType: String?,
        website: String?,
        notes: String?,
    ): IdeaRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO ideas (trip_id, author_id, title, city, link, cost_amount, cost_type, website, notes, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')
            RETURNING id, trip_id, author_id, title, city, link, cost_amount, cost_type, website, notes, status, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(authorId))
            stmt.setString(3, title)
            stmt.setString(4, city)
            stmt.setString(5, link)
            if (costAmount == null) stmt.setNull(6, java.sql.Types.NUMERIC) else stmt.setDouble(6, costAmount)
            stmt.setString(7, costType)
            stmt.setString(8, website)
            stmt.setString(9, notes)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapIdea(rs)
            }
        }
    }

    fun update(
        ideaId: String,
        title: String?,
        city: String?,
        link: String?,
        costAmount: Double?,
        costType: String?,
        website: String?,
        notes: String?,
    ): IdeaRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE ideas
            SET title = COALESCE(?, title),
                city = COALESCE(?, city),
                link = COALESCE(?, link),
                cost_amount = COALESCE(?, cost_amount),
                cost_type = COALESCE(?, cost_type),
                website = COALESCE(?, website),
                notes = COALESCE(?, notes),
                updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            RETURNING id, trip_id, author_id, title, city, link, cost_amount, cost_type, website, notes, status, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, title)
            stmt.setString(2, city)
            stmt.setString(3, link)
            if (costAmount == null) stmt.setNull(4, java.sql.Types.NUMERIC) else stmt.setDouble(4, costAmount)
            stmt.setString(5, costType)
            stmt.setString(6, website)
            stmt.setString(7, notes)
            stmt.setObject(8, UUID.fromString(ideaId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapIdea(rs) else null
            }
        }
    }

    fun updateStatus(ideaId: String, status: String): IdeaRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE ideas
            SET status = ?, updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            RETURNING id, trip_id, author_id, title, city, link, cost_amount, cost_type, website, notes, status, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, status)
            stmt.setObject(2, UUID.fromString(ideaId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapIdea(rs) else null
            }
        }
    }

    fun softDelete(ideaId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE ideas
            SET deleted_at = now(), updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(ideaId))
            stmt.executeUpdate() > 0
        }
    }

    private fun mapIdea(rs: ResultSet): IdeaRow {
        return IdeaRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            tripId = rs.getObject("trip_id", UUID::class.java).toString(),
            authorId = rs.getObject("author_id", UUID::class.java).toString(),
            title = rs.getString("title"),
            city = rs.getString("city"),
            link = rs.getString("link"),
            costAmount = rs.getObject("cost_amount", java.math.BigDecimal::class.java)?.toDouble(),
            costType = rs.getString("cost_type"),
            website = rs.getString("website"),
            notes = rs.getString("notes"),
            status = rs.getString("status"),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }
}
