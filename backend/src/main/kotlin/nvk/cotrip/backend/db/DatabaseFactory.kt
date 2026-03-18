package nvk.cotrip.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import nvk.cotrip.backend.config.DbConfig
import java.io.BufferedReader
import java.sql.Connection

object DatabaseFactory {
    @Volatile
    private var dataSource: HikariDataSource? = null

    @Synchronized
    fun init(config: DbConfig) {
        close()

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val created = HikariDataSource(hikariConfig)
        try {
            applySchema(created)
            dataSource = created
        } catch (ex: Exception) {
            runCatching { created.close() }
            throw ex
        }
    }

    @Synchronized
    fun close() {
        dataSource?.close()
        dataSource = null
    }

    fun dataSource(): HikariDataSource = dataSource
        ?: error("DatabaseFactory is not initialized")

    private fun applySchema(dataSource: HikariDataSource) {
        val resource = javaClass.classLoader.getResourceAsStream("db/schema.sql")
            ?: error("Missing db/schema.sql resource")
        val sql = resource.bufferedReader().use(BufferedReader::readText)
        val statements = splitSqlStatements(sql)

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                statements.forEach { statementSql ->
                    connection.createStatement().use { statement ->
                        statement.execute(statementSql)
                    }
                }
                connection.commit()
            } catch (ex: Exception) {
                runCatching { connection.rollback() }
                throw ex
            } finally {
                connection.autoCommit = false
            }
        }
    }

    private fun splitSqlStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var stringDelimiter = '\u0000'

        fun flush() {
            val value = current.toString().trim()
            if (value.isNotBlank()) {
                statements += value
            }
            current.setLength(0)
        }

        sql.forEach { ch ->
            when {
                !inString && (ch == '\'' || ch == '"') -> {
                    inString = true
                    stringDelimiter = ch
                    current.append(ch)
                }
                inString && ch == stringDelimiter -> {
                    inString = false
                    stringDelimiter = '\u0000'
                    current.append(ch)
                }
                !inString && ch == ';' -> flush()
                else -> current.append(ch)
            }
        }
        flush()

        return statements
            .map { it.lineSequence().filterNot { line -> line.trim().startsWith("--") }.joinToString("\n").trim() }
            .filter { it.isNotBlank() }
    }
}
