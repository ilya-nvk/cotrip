package nvk.cotrip.backend.config

import io.ktor.server.config.ApplicationConfig
import java.util.Locale

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

    val weather = WeatherConfig(
        openWeatherApiKey = System.getenv("OPENWEATHER_API_KEY")
            ?: config.propertyOrNull("ktor.weather.openWeatherApiKey")?.getString(),
        refreshTtlHours = (
            System.getenv("WEATHER_REFRESH_TTL_HOURS")?.toIntOrNull()
                ?: config.propertyOrNull("ktor.weather.refreshTtlHours")?.getString()?.toIntOrNull()
                ?: 8
            ).coerceIn(1, 48),
    )

    val yandexApiKey = System.getenv("YC_AI_API_KEY")
        ?: config.propertyOrNull("ktor.ai.yandexApiKey")?.getString()
    val yandexFolderId = System.getenv("YC_FOLDER_ID")
        ?: config.propertyOrNull("ktor.ai.yandexFolderId")?.getString()
    val aiProvider = (
        System.getenv("ALICE_AI_PROVIDER")
            ?: config.propertyOrNull("ktor.ai.provider")?.getString()
            ?: if (!yandexApiKey.isNullOrBlank() && !yandexFolderId.isNullOrBlank()) "yandex" else "mock"
        ).trim().lowercase()

    val ai = AiConfig(
        provider = aiProvider,
        yandexApiKey = yandexApiKey,
        yandexFolderId = yandexFolderId,
        yandexModel = System.getenv("YC_AI_MODEL")
            ?: config.propertyOrNull("ktor.ai.yandexModel")?.getString()
            ?: "yandexgpt/latest",
        requestTimeoutMillis = (
            System.getenv("YC_AI_TIMEOUT_MS")?.toLongOrNull()
                ?: config.propertyOrNull("ktor.ai.requestTimeoutMillis")?.getString()?.toLongOrNull()
                ?: 25_000L
            ).coerceIn(3_000L, 120_000L),
        maxSuggestions = (
            System.getenv("YC_AI_MAX_SUGGESTIONS")?.toIntOrNull()
                ?: config.propertyOrNull("ktor.ai.maxSuggestions")?.getString()?.toIntOrNull()
                ?: 5
            ).coerceIn(1, 8),
    )

    val media = MediaConfig(
        uploadDir = System.getenv("MEDIA_UPLOAD_DIR")
            ?: config.propertyOrNull("ktor.media.uploadDir")?.getString()
            ?: "uploads",
        maxUploadBytes = (
            System.getenv("MEDIA_MAX_UPLOAD_BYTES")?.toLongOrNull()
                ?: config.propertyOrNull("ktor.media.maxUploadBytes")?.getString()?.toLongOrNull()
                ?: 10L * 1024L * 1024L
            ).coerceIn(512L * 1024L, 50L * 1024L * 1024L),
    )

    val appLinksRawFingerprints = System.getenv("ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS")
        ?: config.propertyOrNull("ktor.appLinks.sha256CertFingerprints")?.getString()
        ?: ""

    val appLinks = AppLinksConfig(
        androidPackage = (
            System.getenv("ANDROID_APP_LINK_PACKAGE")
                ?: config.propertyOrNull("ktor.appLinks.androidPackage")?.getString()
            )
            ?.trim()
            ?.takeIf { it.isNotBlank() },
        sha256CertFingerprints = appLinksRawFingerprints
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.uppercase(Locale.US) },
    )

    val devAuthEnabled = System.getenv("DEV_AUTH_ENABLED")
        ?.toBooleanStrictOrNull()
        ?: config.propertyOrNull("ktor.devAuthEnabled")
            ?.getString()
            ?.toBooleanStrictOrNull()
        ?: false

    return AppConfig(
        jwt = jwt,
        db = db,
        invite = invite,
        weather = weather,
        ai = ai,
        media = media,
        appLinks = appLinks,
        devAuthEnabled = devAuthEnabled
    )
}
