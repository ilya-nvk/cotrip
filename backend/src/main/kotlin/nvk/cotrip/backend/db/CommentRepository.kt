package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

data class CommentRow(
    val id: String,
    val ideaId: String,
    val authorId: String,
    val type: String,
    val body: String,
    val createdAt: OffsetDateTime,
)

object CommentRepository {
    fun countByIdeaIds(ideaIds: List<String>): Map<String, Int> = dbQuery { conn ->
        if (ideaIds.isEmpty()) return@dbQuery emptyMap<String, Int>()
        val placeholders = ideaIds.joinToString(",") { "?" }
        val sql = """
            SELECT idea_id, COUNT(*) AS cnt
            FROM idea_comments
            WHERE idea_id IN ($placeholders) AND deleted_at IS NULL
            GROUP BY idea_id
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            ideaIds.forEachIndexed { idx, id ->
                stmt.setObject(idx + 1, UUID.fromString(id))
            }
            stmt.executeQuery().use { rs ->
                val result = mutableMapOf<String, Int>()
                while (rs.next()) {
                    val ideaId = rs.getObject("idea_id", UUID::class.java).toString()
                    result[ideaId] = rs.getInt("cnt")
                }
                result
            }
        }
    }

    fun listByIdea(ideaId: String): List<CommentRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, idea_id, author_id, type, body, created_at
            FROM idea_comments
            WHERE idea_id = ? AND deleted_at IS NULL
            ORDER BY created_at ASC
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(ideaId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<CommentRow>()
                while (rs.next()) {
                    result += mapComment(rs)
                }
                result
            }
        }
    }

    fun create(ideaId: String, authorId: String, body: String): CommentRow = dbQuery { conn ->
        createWithType(conn, ideaId = ideaId, authorId = authorId, type = "user", body = body)
    }

    fun createSystem(ideaId: String, authorId: String, body: String): CommentRow = dbQuery { conn ->
        createWithType(conn, ideaId = ideaId, authorId = authorId, type = "system", body = body)
    }

    private fun createWithType(
        conn: java.sql.Connection,
        ideaId: String,
        authorId: String,
        type: String,
        body: String,
    ): CommentRow {
        return conn.prepareStatement(
            """
            INSERT INTO idea_comments (idea_id, author_id, type, body)
            VALUES (?, ?, ?, ?)
            RETURNING id, idea_id, author_id, type, body, created_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(ideaId))
            stmt.setObject(2, UUID.fromString(authorId))
            stmt.setString(3, type)
            stmt.setString(4, body)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapComment(rs)
            }
        }
    }

    fun findById(commentId: String): CommentRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, idea_id, author_id, type, body, created_at
            FROM idea_comments
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(commentId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapComment(rs) else null
            }
        }
    }

    fun softDelete(commentId: String, authorId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE idea_comments
            SET deleted_at = now()
            WHERE id = ? AND author_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(commentId))
            stmt.setObject(2, UUID.fromString(authorId))
            stmt.executeUpdate() > 0
        }
    }

    private fun mapComment(rs: ResultSet): CommentRow {
        return CommentRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            ideaId = rs.getObject("idea_id", UUID::class.java).toString(),
            authorId = rs.getObject("author_id", UUID::class.java).toString(),
            type = rs.getString("type"),
            body = rs.getString("body"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
        )
    }
}
