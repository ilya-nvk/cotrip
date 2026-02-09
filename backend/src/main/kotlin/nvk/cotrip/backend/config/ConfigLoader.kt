package nvk.cotrip.backend.config

import io.ktor.server.config.ApplicationConfig

fun loadConfig(config: ApplicationConfig): AppConfig {
    fun requireSetting(envName: String, configKey: String): String {
        val value = System.getenv(envName)
            ?: config.propertyOrNull(configKey)?.getString()
        return value?.takeIf { it.isNotBlank() }
            ?: error("Missing required config: $envName (or $configKey)")
    }

    val jwt = JwtConfig(
        issuer = config.propertyOrNull("ktor.jwt.issuer")?.getString() ?: "cotrip",
        audience = config.propertyOrNull("ktor.jwt.audience")?.getString() ?: "cotrip",
        realm = config.propertyOrNull("ktor.jwt.realm")?.getString() ?: "cotrip",
        secret = config.propertyOrNull("ktor.jwt.secret")?.getString() ?: "dev-secret",
    )

    val db = DbConfig(
        url = requireSetting("DATABASE_URL", "ktor.db.url"),
        user = requireSetting("DATABASE_USER", "ktor.db.user"),
        password = requireSetting("DATABASE_PASSWORD", "ktor.db.password"),
        poolSize = config.propertyOrNull("ktor.db.poolSize")?.getString()?.toInt() ?: 10,
    )

    val invite = InviteConfig(
        baseUrl = System.getenv("INVITE_BASE_URL")
            ?: config.propertyOrNull("ktor.invite.baseUrl")?.getString()
            ?: "http://localhost:8080/invite",
    )

    val devAuthEnabled = System.getenv("DEV_AUTH_ENABLED")
        ?.toBooleanStrictOrNull()
        ?: config.propertyOrNull("ktor.devAuthEnabled")
            ?.getString()
            ?.toBooleanStrictOrNull()
        ?: false

    return AppConfig(jwt = jwt, db = db, invite = invite, devAuthEnabled = devAuthEnabled)
}
