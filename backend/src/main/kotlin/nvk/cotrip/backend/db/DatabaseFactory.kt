package nvk.cotrip.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import nvk.cotrip.backend.config.DbConfig
import org.flywaydb.core.Flyway

object DatabaseFactory {
    private lateinit var dataSource: HikariDataSource

    fun init(config: DbConfig) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    fun dataSource(): HikariDataSource = dataSource
}
