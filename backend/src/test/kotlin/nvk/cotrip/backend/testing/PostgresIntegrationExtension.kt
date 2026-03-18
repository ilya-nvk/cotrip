package nvk.cotrip.backend.testing

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class PostgresIntegrationExtension : BeforeAllCallback, BeforeEachCallback {
    override fun beforeAll(context: ExtensionContext) {
        PostgresContainerSupport.ensureStarted()
    }

    override fun beforeEach(context: ExtensionContext) {
        PostgresContainerSupport.resetDatabase()
    }
}
