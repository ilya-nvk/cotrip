package nvk.cotrip.backend.db

import java.util.UUID

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
}
