package nvk.cotrip.backend.testing

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

object PostgresContainerSupport {
    private val isCi: Boolean =
        System.getenv("CI")?.equals("true", ignoreCase = true) == true

    @Volatile
    private var container: PostgreSQLContainer<*>? = null

    @Synchronized
    fun ensureStarted() {
        if (container?.isRunning == true) return

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
