package nvk.cotrip.backend.config

import io.ktor.server.config.ApplicationConfig

fun loadConfig(config: ApplicationConfig): AppConfig {
    val jwt = JwtConfig(
        issuer = config.propertyOrNull("ktor.jwt.issuer")?.getString() ?: "cotrip",
        audience = config.propertyOrNull("ktor.jwt.audience")?.getString() ?: "cotrip",
        realm = config.propertyOrNull("ktor.jwt.realm")?.getString() ?: "cotrip",
        secret = config.propertyOrNull("ktor.jwt.secret")?.getString() ?: "dev-secret",
    )

    val db = DbConfig(
        url = config.propertyOrNull("ktor.db.url")?.getString()
            ?: "jdbc:postgresql://localhost:5432/cotrip",
        user = config.propertyOrNull("ktor.db.user")?.getString() ?: "cotrip",
        password = config.propertyOrNull("ktor.db.password")?.getString() ?: "cotrip",
        poolSize = config.propertyOrNull("ktor.db.poolSize")?.getString()?.toInt() ?: 10,
    )

    val invite = InviteConfig(
        baseUrl = config.propertyOrNull("ktor.invite.baseUrl")?.getString()
            ?: "http://localhost:8080/invite",
    )

    val devAuthEnabled = config.propertyOrNull("ktor.devAuthEnabled")
        ?.getString()
        ?.toBooleanStrictOrNull()
        ?: false

    return AppConfig(jwt = jwt, db = db, invite = invite, devAuthEnabled = devAuthEnabled)
}
