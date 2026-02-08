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
    val name: String? = null,
    val picture: String? = null,
    val email: String? = null,
)

object GoogleTokenVerifier {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun verify(idToken: String): GoogleTokenInfo? {
        return try {
            client.get("https://oauth2.googleapis.com/tokeninfo") {
                parameter("id_token", idToken)
            }.body()
        } catch (_: Exception) {
            null
        }
    }
}
