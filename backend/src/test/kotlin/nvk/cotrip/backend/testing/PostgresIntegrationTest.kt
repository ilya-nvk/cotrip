package nvk.cotrip.backend.testing

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("container")
@ExtendWith(PostgresIntegrationExtension::class)
annotation class PostgresIntegrationTest
