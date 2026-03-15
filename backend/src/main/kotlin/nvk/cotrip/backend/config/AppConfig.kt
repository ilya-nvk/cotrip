package nvk.cotrip.backend.config

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
    val accessTtlMinutes: Int,
    val refreshTtlDays: Int,
    val maxActiveSessions: Int,
    val googleAllowedAudiences: Set<String>,
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

data class WeatherConfig(
    val openWeatherApiKey: String?,
    val refreshTtlHours: Int,
)

data class AiConfig(
    val provider: String,
    val yandexApiKey: String?,
    val yandexFolderId: String?,
    val yandexModel: String,
    val requestTimeoutMillis: Long,
    val maxSuggestions: Int,
)

data class MediaConfig(
    val uploadDir: String,
    val maxUploadBytes: Long,
)

data class AppLinksConfig(
    val androidPackage: String?,
    val sha256CertFingerprints: List<String>,
)

data class FirebaseConfig(
    val projectId: String?,
    val serviceAccountPath: String?,
)

data class AppConfig(
    val jwt: JwtConfig,
    val db: DbConfig,
    val invite: InviteConfig,
    val weather: WeatherConfig,
    val ai: AiConfig,
    val media: MediaConfig,
    val appLinks: AppLinksConfig,
    val firebase: FirebaseConfig,
    val devAuthEnabled: Boolean,
)
