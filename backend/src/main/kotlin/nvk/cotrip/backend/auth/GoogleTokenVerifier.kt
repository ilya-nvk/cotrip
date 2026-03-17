package nvk.cotrip.backend.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GoogleTokenInfo(
    val sub: String,
    val aud: String? = null,
    val name: String? = null,
    val picture: String? = null,
    val email: String? = null,
)

object GoogleTokenVerifier {
    internal var httpClientForTest: HttpClient? = null
    private val client: HttpClient
        get() = httpClientForTest ?: defaultClient
    private val defaultClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun verify(
        idToken: String,
        allowedAudiences: Set<String>,
    ): GoogleTokenInfo? {
        return try {
            val tokenInfo = client.get("https://oauth2.googleapis.com/tokeninfo") {
                parameter("id_token", idToken)
            }.body<GoogleTokenInfo>()
            val audience = tokenInfo.aud?.trim()
            if (audience.isNullOrBlank()) return null
            if (audience !in allowedAudiences) return null
            tokenInfo
        } catch (_: Exception) {
            null
        }
    }
}
