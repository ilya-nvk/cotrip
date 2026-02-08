package nvk.cotrip.backend.config

import io.ktor.server.config.ApplicationConfig

fun loadConfig(config: ApplicationConfig): AppConfig {
    val jwt = JwtConfig(
        issuer = config.property("ktor.jwt.issuer").getString(),
        audience = config.property("ktor.jwt.audience").getString(),
        realm = config.property("ktor.jwt.realm").getString(),
        secret = config.property("ktor.jwt.secret").getString(),
    )

    val db = DbConfig(
        url = config.property("ktor.db.url").getString(),
        user = config.property("ktor.db.user").getString(),
        password = config.property("ktor.db.password").getString(),
        poolSize = config.property("ktor.db.poolSize").getString().toInt(),
    )

    return AppConfig(jwt = jwt, db = db)
}
