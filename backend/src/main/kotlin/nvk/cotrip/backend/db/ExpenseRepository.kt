package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import nvk.cotrip.backend.limits.LimitReachedException
import nvk.cotrip.backend.limits.Limits
import nvk.cotrip.backend.limits.OldestCandidate

data class ExpenseRow(
    val id: String,
    val tripId: String,
    val title: String,
    val amount: Double,
    val currencyCode: String,
    val status: String,
    val paidById: String?,
    val expenseDate: LocalDate?,
    val splitType: String,
    val note: String?,
    val updatedAt: OffsetDateTime,
)

data class ExpenseParticipantRow(
    val expenseId: String,
    val userId: String,
    val shareAmount: Double?,
    val isIncluded: Boolean,
    val isPaid: Boolean,
    val userName: String? = null,
)

data class ExpensePage(
    val items: List<ExpenseRow>,
    val nextCursor: String?,
)

object ExpenseRepository {
    fun listByTrip(tripId: String): List<ExpenseRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note, updated_at
            FROM expenses
            WHERE trip_id = ? AND deleted_at IS NULL
            ORDER BY updated_at DESC
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ExpenseRow>()
                while (rs.next()) {
                    result += mapExpense(rs)
                }
                result
            }
        }
    }

    fun listByTripPage(
        tripId: String,
        limit: Int,
        cursor: String?,
    ): ExpensePage = dbQuery { conn ->
        val conditions = mutableListOf<String>()
        conditions += "trip_id = ?"
        conditions += "deleted_at IS NULL"

        var cursorUpdatedAt: OffsetDateTime? = null
        var cursorId: String? = null
        if (!cursor.isNullOrBlank()) {
            val decoded = CursorCodec.decode(cursor).split("|")
            if (decoded.size != 2) throw IllegalArgumentException("invalid_cursor")
            cursorUpdatedAt = runCatching { OffsetDateTime.parse(decoded[0]) }.getOrElse {
                throw IllegalArgumentException("invalid_cursor")
            }
            cursorId = runCatching { UUID.fromString(decoded[1]).toString() }.getOrElse {
                throw IllegalArgumentException("invalid_cursor")
            }
            conditions += "(updated_at < ? OR (updated_at = ? AND id < ?))"
        }

        val sql = """
            SELECT id, trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note, updated_at
            FROM expenses
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY updated_at DESC, id DESC
            LIMIT ?
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            var idx = 1
            stmt.setObject(idx++, UUID.fromString(tripId))
            if (cursorUpdatedAt != null && cursorId != null) {
                stmt.setObject(idx++, cursorUpdatedAt)
                stmt.setObject(idx++, cursorUpdatedAt)
                stmt.setObject(idx++, UUID.fromString(cursorId))
            }
            stmt.setInt(idx, limit + 1)

            stmt.executeQuery().use { rs ->
                val fetched = mutableListOf<ExpenseRow>()
                while (rs.next()) {
                    fetched += mapExpense(rs)
                }
                val hasMore = fetched.size > limit
                val items = if (hasMore) fetched.take(limit) else fetched
                val nextCursor = if (hasMore) {
                    val tail = items.last()
                    CursorCodec.encode("${tail.updatedAt}|${tail.id}")
                } else {
                    null
                }
                ExpensePage(items = items, nextCursor = nextCursor)
            }
        }
    }

    fun get(expenseId: String): ExpenseRow? = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note, updated_at
            FROM expenses
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(expenseId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapExpense(rs) else null
            }
        }
    }

    fun listParticipants(expenseIds: List<String>): Map<String, List<ExpenseParticipantRow>> = dbQuery { conn ->
        if (expenseIds.isEmpty()) return@dbQuery emptyMap()
        val placeholders = expenseIds.joinToString(",") { "?" }
        val sql = """
            SELECT
                es.expense_id,
                es.user_id,
                es.share_amount,
                es.is_included,
                es.is_paid,
                u.name AS user_name
            FROM expense_splits es
            LEFT JOIN users u ON u.id = es.user_id
            WHERE es.expense_id IN ($placeholders)
            ORDER BY es.expense_id
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            expenseIds.forEachIndexed { idx, id ->
                stmt.setObject(idx + 1, UUID.fromString(id))
            }
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ExpenseParticipantRow>()
                while (rs.next()) {
                    result += mapParticipant(rs)
                }
                result.groupBy { it.expenseId }
            }
        }
    }

    fun create(
        tripId: String,
        title: String,
        amount: Double,
        currencyCode: String,
        status: String,
        paidById: String?,
        expenseDate: LocalDate?,
        splitType: String,
        note: String?,
        participants: List<ExpenseParticipantRow>,
        expenseId: String? = null,
    ): ExpenseRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT 1
            FROM trips
            WHERE id = ? AND deleted_at IS NULL
            FOR UPDATE
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeQuery()
        }

        val count = conn.prepareStatement(
            """
            SELECT COUNT(*) AS cnt
            FROM expenses
            WHERE trip_id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt("cnt")
            }
        }
        if (count >= Limits.EXPENSES_PER_TRIP) {
            val oldest = conn.prepareStatement(
                """
                SELECT id, title, created_at
                FROM expenses
                WHERE trip_id = ? AND deleted_at IS NULL
                ORDER BY created_at ASC, id ASC
                LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, UUID.fromString(tripId))
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        OldestCandidate(
                            id = rs.getObject("id", UUID::class.java).toString(),
                            label = rs.getString("title"),
                            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                            deletable = true,
                        )
                    } else {
                        null
                    }
                }
            }
            throw LimitReachedException(
                entity = "expense",
                scopeId = tripId,
                limit = Limits.EXPENSES_PER_TRIP,
                currentCount = count,
                oldestCandidate = oldest,
            )
        }

        val expense = conn.prepareStatement(
            """
            INSERT INTO expenses (id, trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note)
            VALUES (COALESCE(?, gen_random_uuid()), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note, updated_at
            """.trimIndent()
        ).use { stmt ->
            if (expenseId == null) {
                stmt.setNull(1, java.sql.Types.OTHER)
            } else {
                stmt.setObject(1, UUID.fromString(expenseId))
            }
            stmt.setObject(2, UUID.fromString(tripId))
            stmt.setString(3, title)
            stmt.setDouble(4, amount)
            stmt.setString(5, currencyCode)
            stmt.setString(6, status)
            if (paidById == null) stmt.setNull(7, java.sql.Types.OTHER) else stmt.setObject(7, UUID.fromString(paidById))
            if (expenseDate == null) stmt.setNull(8, java.sql.Types.DATE) else stmt.setObject(8, expenseDate)
            stmt.setString(9, splitType)
            stmt.setString(10, note)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapExpense(rs)
            }
        }

        upsertParticipants(conn, expense.id, participants)
        expense
    }

    fun update(
        expenseId: String,
        title: String?,
        amount: Double?,
        status: String?,
        paidById: String?,
        expenseDate: LocalDate?,
        splitType: String?,
        note: String?,
        participants: List<ExpenseParticipantRow>?,
    ): ExpenseRow? = dbQuery { conn ->
        val expense = conn.prepareStatement(
            """
            UPDATE expenses
            SET title = COALESCE(?, title),
                amount = COALESCE(?, amount),
                status = COALESCE(?, status),
                paid_by = COALESCE(?, paid_by),
                expense_date = COALESCE(?, expense_date),
                split_type = COALESCE(?, split_type),
                note = COALESCE(?, note),
                updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            RETURNING id, trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, title)
            if (amount == null) stmt.setNull(2, java.sql.Types.NUMERIC) else stmt.setDouble(2, amount)
            stmt.setString(3, status)
            if (paidById == null) stmt.setNull(4, java.sql.Types.OTHER) else stmt.setObject(4, UUID.fromString(paidById))
            if (expenseDate == null) stmt.setNull(5, java.sql.Types.DATE) else stmt.setObject(5, expenseDate)
            stmt.setString(6, splitType)
            stmt.setString(7, note)
            stmt.setObject(8, UUID.fromString(expenseId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapExpense(rs) else null
            }
        }

        if (expense != null && participants != null) {
            conn.prepareStatement(
                """
                DELETE FROM expense_splits WHERE expense_id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, UUID.fromString(expenseId))
                stmt.executeUpdate()
            }
            upsertParticipants(conn, expenseId, participants)
        }

        expense
    }

    fun softDelete(expenseId: String): Boolean = dbQuery { conn ->
        conn.prepareStatement(
            """
            UPDATE expenses
            SET deleted_at = now(), updated_at = now()
            WHERE id = ? AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(expenseId))
            stmt.executeUpdate() > 0
        }
    }

    private fun upsertParticipants(conn: java.sql.Connection, expenseId: String, participants: List<ExpenseParticipantRow>) {
        if (participants.isEmpty()) return
        conn.prepareStatement(
            """
            INSERT INTO expense_splits (expense_id, user_id, share_amount, is_included, is_paid)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            participants.forEach { participant ->
                stmt.setObject(1, UUID.fromString(expenseId))
                stmt.setObject(2, UUID.fromString(participant.userId))
                if (participant.shareAmount == null) stmt.setNull(3, java.sql.Types.NUMERIC) else stmt.setDouble(3, participant.shareAmount)
                stmt.setBoolean(4, participant.isIncluded)
                stmt.setBoolean(5, participant.isPaid)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun mapExpense(rs: ResultSet): ExpenseRow {
        return ExpenseRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            tripId = rs.getObject("trip_id", UUID::class.java).toString(),
            title = rs.getString("title"),
            amount = rs.getBigDecimal("amount").toDouble(),
            currencyCode = rs.getString("currency_code"),
            status = rs.getString("status"),
            paidById = rs.getObject("paid_by", UUID::class.java)?.toString(),
            expenseDate = rs.getObject("expense_date", LocalDate::class.java),
            splitType = rs.getString("split_type"),
            note = rs.getString("note"),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
        )
    }

    private fun mapParticipant(rs: ResultSet): ExpenseParticipantRow {
        return ExpenseParticipantRow(
            expenseId = rs.getObject("expense_id", UUID::class.java).toString(),
            userId = rs.getObject("user_id", UUID::class.java).toString(),
            shareAmount = rs.getObject("share_amount", java.math.BigDecimal::class.java)?.toDouble(),
            isIncluded = rs.getBoolean("is_included"),
            isPaid = rs.getBoolean("is_paid"),
            userName = rs.getString("user_name"),
        )
    }
}
