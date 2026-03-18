package nvk.cotrip.backend.config

import io.ktor.server.config.MapApplicationConfig
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigLoaderTest {

    @Test
    fun given_mapConfigWithOverrides_when_loadConfig_then_appliesFallbacksTrimmingAndClamping() {
        // GIVEN
        val config = MapApplicationConfig(
            "ktor.jwt.issuer" to "issuer",
            "ktor.jwt.audience" to "audience",
            "ktor.jwt.realm" to "realm",
            "ktor.jwt.secret" to "secret",
            "ktor.jwt.accessTtlMinutes" to "500",
            "ktor.jwt.refreshTtlDays" to "0",
            "ktor.jwt.maxActiveSessions" to "100",
            "ktor.jwt.googleAllowedAudiences" to "aud-1, aud-2 ;\naud-3",
            "ktor.db.url" to "jdbc:postgresql://localhost:5432/cotrip",
            "ktor.db.user" to "cotrip",
            "ktor.db.password" to "password",
            "ktor.db.poolSize" to "12",
            "ktor.invite.baseUrl" to "http://localhost:8080/invite",
            "ktor.weather.refreshTtlHours" to "200",
            "ktor.ai.provider" to "Yandex",
            "ktor.ai.yandexApiKey" to "cfg-key",
            "ktor.ai.yandexFolderId" to "cfg-folder",
            "ktor.ai.yandexModel" to "yandexgpt/latest",
            "ktor.ai.requestTimeoutMillis" to "1000000",
            "ktor.ai.maxSuggestions" to "99",
            "ktor.media.uploadDir" to "uploads-test",
            "ktor.media.maxUploadBytes" to "1",
            "ktor.appLinks.androidPackage" to " nvk.cotrip.app ",
            "ktor.appLinks.sha256CertFingerprints" to "aa:bb, cc:dd ;\nee:ff",
            "ktor.firebase.projectId" to " firebase-project ",
            "ktor.firebase.serviceAccountPath" to " /tmp/firebase.json ",
            "ktor.devAuthEnabled" to "true",
        )

        // WHEN
        val loaded = loadConfig(config)

        // THEN — expected values follow loadConfig logic: env overrides config, then clamping/trimming apply.
        // If env vars (e.g. JWT_ACCESS_TTL_MINUTES) are set, assertions depend on them; run without extra env for stable fallbacks.
        val expectedAccessTtl = (System.getenv("JWT_ACCESS_TTL_MINUTES")?.toIntOrNull() ?: 500)
            .coerceIn(1, 120)
        val expectedRefreshTtl = (System.getenv("JWT_REFRESH_TTL_DAYS")?.toIntOrNull() ?: 0)
            .coerceIn(1, 365)
        val expectedMaxSessions = (System.getenv("AUTH_MAX_ACTIVE_SESSIONS")?.toIntOrNull() ?: 100)
            .coerceIn(1, 20)
        val expectedWeatherTtl = (System.getenv("WEATHER_REFRESH_TTL_HOURS")?.toIntOrNull() ?: 200)
            .coerceIn(1, 48)
        val expectedTimeout = (System.getenv("YC_AI_TIMEOUT_MS")?.toLongOrNull() ?: 1_000_000L)
            .coerceIn(3_000L, 120_000L)
        val expectedMaxSuggestions = (System.getenv("YC_AI_MAX_SUGGESTIONS")?.toIntOrNull() ?: 99)
            .coerceIn(1, 8)
        val expectedMediaBytes = (System.getenv("MEDIA_MAX_UPLOAD_BYTES")?.toLongOrNull() ?: 1L)
            .coerceIn(512L * 1024L, 50L * 1024L * 1024L)
        val expectedAudiences = (
            System.getenv("GOOGLE_ALLOWED_AUDIENCES") ?: "aud-1, aud-2 ;\naud-3"
            )
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val expectedProvider = (
            System.getenv("ALICE_AI_PROVIDER") ?: "Yandex"
            )
            .trim()
            .lowercase()
        val expectedAppPackage = (
            System.getenv("ANDROID_APP_LINK_PACKAGE") ?: " nvk.cotrip.app "
            )
            .trim()
            .ifBlank { null }
        val expectedFingerprints = (
            System.getenv("ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS") ?: "aa:bb, cc:dd ;\nee:ff"
            )
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.uppercase(Locale.US) }
        val expectedDevAuth = System.getenv("DEV_AUTH_ENABLED")
            ?.toBooleanStrictOrNull()
            ?: true

        assertEquals(expectedAccessTtl, loaded.jwt.accessTtlMinutes)
        assertEquals(expectedRefreshTtl, loaded.jwt.refreshTtlDays)
        assertEquals(expectedMaxSessions, loaded.jwt.maxActiveSessions)
        assertEquals(expectedAudiences, loaded.jwt.googleAllowedAudiences)
        assertEquals(expectedWeatherTtl, loaded.weather.refreshTtlHours)
        assertEquals(expectedProvider, loaded.ai.provider)
        assertEquals(expectedTimeout, loaded.ai.requestTimeoutMillis)
        assertEquals(expectedMaxSuggestions, loaded.ai.maxSuggestions)
        assertEquals(expectedMediaBytes, loaded.media.maxUploadBytes)
        assertEquals(expectedAppPackage, loaded.appLinks.androidPackage)
        assertEquals(expectedFingerprints, loaded.appLinks.sha256CertFingerprints)
        assertEquals(expectedDevAuth, loaded.devAuthEnabled)
        assertEquals(12, loaded.db.poolSize)
        assertTrue(loaded.db.url.isNotBlank())
        assertTrue(loaded.db.user.isNotBlank())
        assertTrue(loaded.db.password.isNotBlank())
    }

    @Test
    fun given_configWithZeroAndNegativeTtls_when_loadConfig_then_clampsToMinimum() {
        val config = MapApplicationConfig(
            "ktor.jwt.issuer" to "i",
            "ktor.jwt.audience" to "a",
            "ktor.jwt.realm" to "r",
            "ktor.jwt.secret" to "s",
            "ktor.jwt.accessTtlMinutes" to "0",
            "ktor.jwt.refreshTtlDays" to "0",
            "ktor.jwt.maxActiveSessions" to "0",
            "ktor.jwt.googleAllowedAudiences" to "aud",
            "ktor.db.url" to "jdbc:postgresql://localhost/db",
            "ktor.db.user" to "u",
            "ktor.db.password" to "p",
            "ktor.weather.refreshTtlHours" to "0",
            "ktor.ai.provider" to "mock",
            "ktor.ai.maxSuggestions" to "0",
            "ktor.media.uploadDir" to "d",
            "ktor.media.maxUploadBytes" to "100",
            "ktor.devAuthEnabled" to "false",
        )
        val loaded = loadConfig(config)
        assertEquals(1, loaded.jwt.accessTtlMinutes)
        assertEquals(1, loaded.jwt.refreshTtlDays)
        assertEquals(1, loaded.jwt.maxActiveSessions)
        assertEquals(1, loaded.weather.refreshTtlHours)
        assertEquals(1, loaded.ai.maxSuggestions)
        assertTrue(loaded.media.maxUploadBytes >= 512L * 1024L)
    }

    @Test
    fun given_configWithEmptyGoogleAudiences_when_loadConfig_then_returnsEmptySet() {
        val config = MapApplicationConfig(
            "ktor.jwt.issuer" to "i",
            "ktor.jwt.audience" to "a",
            "ktor.jwt.realm" to "r",
            "ktor.jwt.secret" to "s",
            "ktor.jwt.accessTtlMinutes" to "15",
            "ktor.jwt.refreshTtlDays" to "30",
            "ktor.jwt.maxActiveSessions" to "5",
            "ktor.jwt.googleAllowedAudiences" to "  ;  ,  ",
            "ktor.db.url" to "jdbc:postgresql://localhost/db",
            "ktor.db.user" to "u",
            "ktor.db.password" to "p",
            "ktor.weather.refreshTtlHours" to "8",
            "ktor.ai.provider" to "mock",
            "ktor.media.uploadDir" to "d",
            "ktor.media.maxUploadBytes" to "1048576",
            "ktor.devAuthEnabled" to "false",
        )
        val loaded = loadConfig(config)
        assertTrue(loaded.jwt.googleAllowedAudiences.isEmpty())
    }
}
