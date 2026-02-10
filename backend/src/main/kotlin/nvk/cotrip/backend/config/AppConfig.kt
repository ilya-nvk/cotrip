package nvk.cotrip.backend.config

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
)

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int,
)

data class InviteConfig(
    val baseUrl: String,
)

data class GoogleMapsConfig(
    val apiKey: String? = null,
)

data class AppConfig(
    val jwt: JwtConfig,
    val db: DbConfig,
    val invite: InviteConfig,
    val googleMaps: GoogleMapsConfig,
    val devAuthEnabled: Boolean,
)
