package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

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
            SELECT expense_id, user_id, share_amount, is_included, is_paid
            FROM expense_splits
            WHERE expense_id IN ($placeholders)
            ORDER BY expense_id
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
    ): ExpenseRow = dbQuery { conn ->
        val expense = conn.prepareStatement(
            """
            INSERT INTO expenses (trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, trip_id, title, amount, currency_code, status, paid_by, expense_date, split_type, note, updated_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setString(2, title)
            stmt.setDouble(3, amount)
            stmt.setString(4, currencyCode)
            stmt.setString(5, status)
            if (paidById == null) stmt.setNull(6, java.sql.Types.OTHER) else stmt.setObject(6, UUID.fromString(paidById))
            if (expenseDate == null) stmt.setNull(7, java.sql.Types.DATE) else stmt.setObject(7, expenseDate)
            stmt.setString(8, splitType)
            stmt.setString(9, note)
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
        )
    }
}
