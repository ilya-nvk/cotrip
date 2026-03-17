package nvk.cotrip.backend.testing

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.sql.DriverManager

object PostgresContainerSupport {
    private val isCi: Boolean =
        System.getenv("CI")?.equals("true", ignoreCase = true) == true

    @Volatile
    private var container: PostgreSQLContainer<*>? = null

    @Volatile
    private var schemaApplied: Boolean = false

    @Synchronized
    fun ensureStarted() {
        if (container?.isRunning == true) {
            ensureSchema()
            return
        }

        val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable() }
            .getOrDefault(false)
        if (!dockerAvailable) {
            if (isCi) {
                error("Docker is required for backend integration tests in CI, but it is unavailable.")
            }
            assumeTrue(
                false,
                "Docker is unavailable locally; skipping container-backed backend integration tests."
            )
        }

        container = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("cotrip_test")
            withUsername("cotrip")
            withPassword("cotrip")
            start()
        }
        ensureSchema()
    }

    private fun ensureSchema() {
        if (schemaApplied) return
        val c = requireNotNull(container)
        val sqlText = loadSchemaSql()
        val statements = splitSqlStatements(sqlText)
        // CREATE EXTENSION requires superuser; try postgres first (official image default), then cotrip without extension (PG13+ has gen_random_uuid())
        try {
            DriverManager.getConnection(c.jdbcUrl, "postgres", "postgres").use { conn ->
                executeSchema(conn, statements)
            }
        } catch (_: Exception) {
            val withoutExtension = statements.filterNot { it.trim().uppercase().startsWith("CREATE EXTENSION") }
            DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
                executeSchema(conn, withoutExtension)
            }
        }
        schemaApplied = true
    }

    private fun executeSchema(conn: java.sql.Connection, statements: List<String>) {
        conn.autoCommit = false
        try {
            statements.forEach { stmt ->
                conn.createStatement().use { it.execute(stmt) }
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    private fun loadSchemaSql(): String {
        PostgresContainerSupport::class.java.classLoader
            .getResourceAsStream("db/schema.sql")
            ?.use { return it.bufferedReader().readText() }
        for (path in listOf("src/main/resources/db/schema.sql", "backend/src/main/resources/db/schema.sql")) {
            val file = File(path)
            if (file.isFile) return file.readText()
        }
        error("Missing db/schema.sql: not on classpath and not found at src/main/resources/db/schema.sql or backend/src/main/resources/db/schema.sql")
    }

    private fun splitSqlStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var stringDelimiter = '\u0000'
        fun flush() {
            val value = current.toString().trim()
            if (value.isNotBlank()) statements += value
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

    fun jdbcUrl(): String {
        ensureStarted()
        return requireNotNull(container).jdbcUrl
    }

    fun username(): String {
        ensureStarted()
        return requireNotNull(container).username
    }

    fun password(): String {
        ensureStarted()
        return requireNotNull(container).password
    }

    fun resetDatabase() {
        ensureStarted()
        DriverManager.getConnection(jdbcUrl(), username(), password()).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    DO $$
                    DECLARE
                      truncate_sql text;
                    BEGIN
                      SELECT
                        'TRUNCATE TABLE ' || string_agg(format('%I.%I', schemaname, tablename), ', ')
                        || ' RESTART IDENTITY CASCADE'
                      INTO truncate_sql
                      FROM pg_tables
                      WHERE schemaname = 'public';
                      IF truncate_sql IS NOT NULL THEN
                        EXECUTE truncate_sql;
                      END IF;
                    END $$;
                    """.trimIndent()
                )
            }
        }
    }
}
