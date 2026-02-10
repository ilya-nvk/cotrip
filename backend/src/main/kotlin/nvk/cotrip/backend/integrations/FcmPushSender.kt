package nvk.cotrip.backend.integrations

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val FCM_LEGACY_URL = "https://fcm.googleapis.com/fcm/send"

object FcmPushSender {
    private val logger = LoggerFactory.getLogger(FcmPushSender::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = HttpClient(CIO) {
        install(HttpTimeout)
        install(ContentNegotiation) { json(json) }
    }

    @Volatile
    private var serverKey: String? = null

    fun init(serverKey: String?) {
        this.serverKey = serverKey?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun send(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ): Set<String> {
        val key = serverKey ?: return emptySet()
        val uniqueTokens = tokens.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueTokens.isEmpty()) return emptySet()

        return runCatching {
            val response = client.post(FCM_LEGACY_URL) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "key=$key")
                setBody(
                    FcmLegacyRequest(
                        registrationIds = uniqueTokens,
                        notification = FcmNotification(title = title, body = body),
                        data = data
                    )
                )
            }
            val parsed: FcmLegacyResponse = response.body()
            val invalid = mutableSetOf<String>()
            parsed.results.forEachIndexed { index, result ->
                val token = uniqueTokens.getOrNull(index) ?: return@forEachIndexed
                val error = result.error.orEmpty()
                if (error == "NotRegistered" || error == "InvalidRegistration") {
                    invalid += token
                }
            }
            invalid
        }.onFailure { error ->
            logger.warn("FCM send failed: ${error.message}")
        }.getOrElse { emptySet() }
    }
}

@Serializable
private data class FcmLegacyRequest(
    @SerialName("registration_ids")
    val registrationIds: List<String>,
    val priority: String = "high",
    val notification: FcmNotification,
    val data: Map<String, String> = emptyMap(),
)

@Serializable
private data class FcmNotification(
    val title: String,
    val body: String,
)

@Serializable
private data class FcmLegacyResponse(
    val success: Int = 0,
    val failure: Int = 0,
    val results: List<FcmLegacyResult> = emptyList(),
)

@Serializable
private data class FcmLegacyResult(
    @SerialName("message_id")
    val messageId: String? = null,
    val error: String? = null,
)
