package nvk.cotrip.backend.integrations

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nvk.cotrip.backend.config.AiConfig
import nvk.cotrip.backend.db.AiSuggestionInput

data class YandexTripSuggestionPrompt(
    val city: String?,
    val description: String?,
    val typeOptions: List<String>,
    val timeOfDayOptions: List<String>,
    val budgetOptions: List<String>,
    val currencyCode: String,
    val generationToken: String?,
    val language: String?,
    val maxSuggestions: Int,
)

object YandexAiClient {
    private const val CHAT_COMPLETIONS_URL = "https://ai.api.cloud.yandex.net/v1/chat/completions"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout)
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun generateSuggestions(
        config: AiConfig,
        prompt: YandexTripSuggestionPrompt,
    ): List<AiSuggestionInput> {
        val apiKey = config.yandexApiKey?.trim().orEmpty()
        val folderId = config.yandexFolderId?.trim().orEmpty()
        require(apiKey.isNotBlank()) { "YC_AI_API_KEY is required for Yandex provider" }
        require(folderId.isNotBlank()) { "YC_FOLDER_ID is required for Yandex provider" }

        val model = resolveModel(folderId, config.yandexModel)
        val schema = suggestionSchema(prompt.maxSuggestions)
        val requestBody = buildJsonObject {
            put("model", model)
            put("temperature", 0.55)
            put("max_tokens", 1200)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", buildSystemPrompt(prompt.maxSuggestions))
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", buildUserPrompt(prompt))
                        }
                    )
                }
            )
            put(
                "response_format",
                buildJsonObject {
                    put("type", "json_schema")
                    put(
                        "json_schema",
                        buildJsonObject {
                            put("name", "trip_suggestions")
                            put("description", "Travel activity suggestions for a trip")
                            put("schema", schema)
                            put("strict", true)
                        }
                    )
                }
            )
        }

        val response = httpClient.post(CHAT_COMPLETIONS_URL) {
            timeout {
                requestTimeoutMillis = config.requestTimeoutMillis
            }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("OpenAI-Project", folderId)
            setBody(requestBody)
        }
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
            throw YandexAiException(
                statusCode = response.status.value,
                details = "Yandex chat.completions failed with ${response.status.value}: ${errorBody.take(1_000)}",
            )
        }
        val responseBody = response.body<JsonObject>()

        val contentNode = responseBody["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
        val rawContent = extractMessageContent(contentNode)

        if (rawContent.isBlank()) {
            throw YandexAiException(
                details = "Yandex returned empty suggestion content: ${responseBody.toString().take(1_000)}"
            )
        }

        return parseSuggestions(rawContent, prompt.maxSuggestions)
    }

    private fun resolveModel(folderId: String, configuredModel: String): String {
        val value = configuredModel.trim()
        if (value.startsWith("gpt://")) return value
        val suffix = value.trimStart('/')
        return "gpt://$folderId/$suffix"
    }

    private fun buildSystemPrompt(maxSuggestions: Int): String {
        return """
            You are CoTrip AI planner.
            Generate between 1 and $maxSuggestions practical travel suggestions.
            Return strictly valid JSON that follows the given schema.
            Keep suggestions concrete, diverse, and realistic.
            Avoid markdown and avoid text outside JSON.
        """.trimIndent()
    }

    private fun buildUserPrompt(prompt: YandexTripSuggestionPrompt): String {
        val city = prompt.city?.trim().orEmpty().ifBlank { "any city from the trip" }
        val description = prompt.description?.trim().orEmpty().ifBlank { "No additional description." }
        val typeLine = prompt.typeOptions.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Any types"
        val timeLine = prompt.timeOfDayOptions.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Any time of day"
        val budgetLine = prompt.budgetOptions.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Any budget"
        val generationToken = prompt.generationToken?.trim().orEmpty().ifBlank { "none" }
        val language = prompt.language?.trim().orEmpty().ifBlank { "en" }
        return """
            Trip city: $city
            Trip currency code: ${prompt.currencyCode}
            User description: $description
            Type options: $typeLine
            Time of day options: $timeLine
            Budget options: $budgetLine
            Generation token: $generationToken
            Response language: $language
            Output rule: estimatedCost must be a numeric amount in trip currency (${prompt.currencyCode}).
            For different generation tokens, produce a different set of suggestions.
            Output rule: all human-readable fields must be in the response language.
        """.trimIndent()
    }

    private fun suggestionSchema(maxSuggestions: Int): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("additionalProperties", JsonPrimitive(false))
            put("required", JsonArray(listOf(JsonPrimitive("items"))))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "items",
                        buildJsonObject {
                            put("type", "array")
                            put("minItems", 1)
                            put("maxItems", maxSuggestions)
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", "object")
                                    put("additionalProperties", JsonPrimitive(false))
                                    put(
                                        "required",
                                        JsonArray(
                                            listOf(
                                                JsonPrimitive("title"),
                                                JsonPrimitive("description"),
                                                JsonPrimitive("typeLabel"),
                                                JsonPrimitive("durationLabel"),
                                                JsonPrimitive("budgetLabel"),
                                                JsonPrimitive("estimatedCost"),
                                            )
                                        )
                                    )
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("title", buildJsonObject { put("type", "string") })
                                            put(
                                                "description",
                                                buildJsonObject {
                                                    put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
                                                }
                                            )
                                            put(
                                                "typeLabel",
                                                buildJsonObject {
                                                    put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
                                                }
                                            )
                                            put(
                                                "durationLabel",
                                                buildJsonObject {
                                                    put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
                                                }
                                            )
                                            put(
                                                "budgetLabel",
                                                buildJsonObject {
                                                    put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
                                                }
                                            )
                                            put(
                                                "estimatedCost",
                                                buildJsonObject {
                                                    put("type", JsonArray(listOf(JsonPrimitive("number"), JsonPrimitive("null"))))
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    private fun parseSuggestions(rawContent: String, maxSuggestions: Int): List<AiSuggestionInput> {
        val normalized = unwrapJson(rawContent)
        val root = json.parseToJsonElement(normalized).jsonObject
        val items = root["items"]?.jsonArray.orEmpty()
        return items
            .mapNotNull { entry ->
                val item = entry.jsonObject
                val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().take(120)
                if (title.isBlank()) return@mapNotNull null
                val description = item["description"]?.jsonPrimitive?.contentOrNull?.trim()?.take(500)
                val typeLabel = item["typeLabel"]?.jsonPrimitive?.contentOrNull?.trim()?.take(64)
                val durationLabel = item["durationLabel"]?.jsonPrimitive?.contentOrNull?.trim()?.take(64)
                val budgetLabel = item["budgetLabel"]?.jsonPrimitive?.contentOrNull?.trim()?.take(64)
                val estimatedCost = item["estimatedCost"]?.jsonPrimitive?.doubleOrNull
                AiSuggestionInput(
                    title = title,
                    description = description,
                    typeLabel = typeLabel,
                    durationLabel = durationLabel,
                    budgetLabel = budgetLabel,
                    estimatedCost = estimatedCost?.takeIf { it.isFinite() && it >= 0.0 },
                )
            }
            .distinctBy { it.title.lowercase() }
            .take(maxSuggestions)
    }

    private fun unwrapJson(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractMessageContent(contentNode: kotlinx.serialization.json.JsonElement?): String {
        return when (contentNode) {
            null -> ""
            is JsonPrimitive -> contentNode.contentOrNull.orEmpty().trim()
            is JsonArray -> {
                contentNode
                    .mapNotNull { part ->
                        val obj = part as? JsonObject ?: return@mapNotNull null
                        obj["text"]?.jsonPrimitive?.contentOrNull
                            ?: obj["content"]?.jsonPrimitive?.contentOrNull
                    }
                    .joinToString(separator = "")
                    .trim()
            }
            else -> contentNode.toString().trim()
        }
    }

    class YandexAiException(
        val statusCode: Int? = null,
        val details: String,
    ) : RuntimeException(details)
}
