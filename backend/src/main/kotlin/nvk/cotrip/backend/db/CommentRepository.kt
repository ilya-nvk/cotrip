package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import nvk.cotrip.backend.limits.LimitReachedException
import nvk.cotrip.backend.limits.Limits
import nvk.cotrip.backend.limits.OldestCandidate

data class CommentRow(
    val id: String,
    val ideaId: String,
    val authorId: String,
    val type: String,
    val body: String,
    val createdAt: OffsetDateTime,
)

data class CommentPage(
    val items: List<CommentRow>,
    val nextCursor: String?,
)

object CommentRepository {
    fun ideaIdsWithUserCommentHistory(ideaIds: List<String>): Set<String> = dbQuery { conn ->
        if (ideaIds.isEmpty()) return@dbQuery emptySet()
        val placeholders = ideaIds.joinToString(",") { "?" }
        val sql = """
            SELECT DISTINCT idea_id::text AS idea_id
            FROM idea_comments
            WHERE idea_id IN ($placeholders) AND type = 'user'
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            ideaIds.forEachIndexed { idx, id ->
                stmt.setObject(idx + 1, UUID.fromString(id))
            }
            stmt.executeQuery().use { rs ->
                val result = mutableSetOf<String>()
                while (rs.next()) {
                    result += rs.getString("idea_id")
                }
                result
            }
        }
    }

    fun countByIdeaIds(ideaIds: List<String>): Map<String, Int> = dbQuery { conn ->
        if (ideaIds.isEmpty()) return@dbQuery emptyMap<String, Int>()
        val placeholders = ideaIds.joinToString(",") { "?" }
        val sql = """
            SELECT idea_id, COUNT(*) AS cnt
            FROM idea_comments
            WHERE idea_id IN ($placeholders) AND deleted_at IS NULL AND type = 'user'
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

    fun listByIdeaPage(
        ideaId: String,
        limit: Int,
        cursor: String?,
    ): CommentPage = dbQuery { conn ->
        val conditions = mutableListOf<String>()
        conditions += "idea_id = ?"
        conditions += "deleted_at IS NULL"

        var cursorCreatedAt: OffsetDateTime? = null
        var cursorId: String? = null
        if (!cursor.isNullOrBlank()) {
            val decoded = CursorCodec.decode(cursor).split("|")
            if (decoded.size != 2) throw IllegalArgumentException("invalid_cursor")
            cursorCreatedAt = runCatching { OffsetDateTime.parse(decoded[0]) }.getOrElse {
                throw IllegalArgumentException("invalid_cursor")
            }
            cursorId = runCatching { UUID.fromString(decoded[1]).toString() }.getOrElse {
                throw IllegalArgumentException("invalid_cursor")
            }
            conditions += "(created_at > ? OR (created_at = ? AND id > ?))"
        }

        val sql = """
            SELECT id, idea_id, author_id, type, body, created_at
            FROM idea_comments
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY created_at ASC, id ASC
            LIMIT ?
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            var idx = 1
            stmt.setObject(idx++, UUID.fromString(ideaId))
            if (cursorCreatedAt != null && cursorId != null) {
                stmt.setObject(idx++, cursorCreatedAt)
                stmt.setObject(idx++, cursorCreatedAt)
                stmt.setObject(idx++, UUID.fromString(cursorId))
            }
            stmt.setInt(idx, limit + 1)

            stmt.executeQuery().use { rs ->
                val fetched = mutableListOf<CommentRow>()
                while (rs.next()) {
                    fetched += mapComment(rs)
                }
                val hasMore = fetched.size > limit
                val items = if (hasMore) fetched.take(limit) else fetched
                val nextCursor = if (hasMore) {
                    val tail = items.last()
                    CursorCodec.encode("${tail.createdAt}|${tail.id}")
                } else {
                    null
                }
                CommentPage(items = items, nextCursor = nextCursor)
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
        if (type == "user") {
            conn.prepareStatement(
                """
                SELECT 1
                FROM ideas
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, UUID.fromString(ideaId))
                stmt.executeQuery()
            }

            val count = conn.prepareStatement(
                """
                SELECT COUNT(*) AS cnt
                FROM idea_comments
                WHERE idea_id = ? AND deleted_at IS NULL AND type = 'user'
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, UUID.fromString(ideaId))
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt("cnt")
                }
            }

            if (count >= Limits.COMMENTS_PER_IDEA) {
                val oldest = conn.prepareStatement(
                    """
                    SELECT id, body, created_at, author_id
                    FROM idea_comments
                    WHERE idea_id = ? AND deleted_at IS NULL AND type = 'user'
                    ORDER BY created_at ASC, id ASC
                    LIMIT 1
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setObject(1, UUID.fromString(ideaId))
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val oldestAuthor = rs.getObject("author_id", UUID::class.java).toString()
                            OldestCandidate(
                                id = rs.getObject("id", UUID::class.java).toString(),
                                label = rs.getString("body"),
                                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                                deletable = oldestAuthor == authorId,
                            )
                        } else {
                            null
                        }
                    }
                }
                throw LimitReachedException(
                    entity = "comment",
                    scopeId = ideaId,
                    limit = Limits.COMMENTS_PER_IDEA,
                    currentCount = count,
                    oldestCandidate = oldest,
                )
            }
        }

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
