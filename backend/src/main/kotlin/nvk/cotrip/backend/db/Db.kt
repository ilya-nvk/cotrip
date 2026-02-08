package nvk.cotrip.backend.db

import java.sql.Connection

inline fun <T> dbQuery(block: (Connection) -> T): T {
    val dataSource = DatabaseFactory.dataSource()
    dataSource.connection.use { connection ->
        return try {
            val result = block(connection)
            connection.commit()
            result
        } catch (ex: Exception) {
            runCatching { connection.rollback() }
            throw ex
        }
    }
}
