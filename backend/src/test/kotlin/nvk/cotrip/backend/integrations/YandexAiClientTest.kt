package nvk.cotrip.backend.integrations

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nvk.cotrip.backend.config.AiConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YandexAiClientTest {

    @Test
    fun given_noApiKey_when_generateSuggestions_then_throwsWithYcAiApiKeyMessage() = runBlocking {
        // GIVEN
        val config = baseConfig(
            apiKey = " ",
            folderId = "folder-id",
        )

        // WHEN
        val error = assertFailsWith<IllegalArgumentException> {
            runBlocking { YandexAiClient.generateSuggestions(config, prompt()) }
        }

        // THEN
        assertTrue(error.message.orEmpty().contains("YC_AI_API_KEY"))
    }

    @Test
    fun given_noFolderId_when_generateSuggestions_then_throwsWithYcFolderIdMessage() = runBlocking {
        // GIVEN
        val config = baseConfig(
            apiKey = "api-key",
            folderId = " ",
        )

        // WHEN
        val error = assertFailsWith<IllegalArgumentException> {
            runBlocking { YandexAiClient.generateSuggestions(config, prompt()) }
        }

        // THEN
        assertTrue(error.message.orEmpty().contains("YC_FOLDER_ID"))
    }

    @Test
    fun given_gptModelPath_when_resolveModel_then_returnsConfiguredValueAsIs() {
        // WHEN
        val resolved = resolveModel("folder-1", "gpt://folder-2/yandexgpt/latest")

        // THEN
        assertEquals("gpt://folder-2/yandexgpt/latest", resolved)
    }

    @Test
    fun given_relativeModelPath_when_resolveModel_then_prefixesFolderIdAndTrimsSlash() {
        // WHEN
        val resolved = resolveModel("folder-1", " /yandexgpt/latest ")

        // THEN
        assertEquals("gpt://folder-1/yandexgpt/latest", resolved)
    }

    @Test
    fun given_blankPromptFields_when_buildUserPrompt_then_usesFallbackValues() {
        // GIVEN
        val prompt = YandexTripSuggestionPrompt(
            city = " ",
            description = " ",
            typeOptions = emptyList(),
            timeOfDayOptions = emptyList(),
            budgetOptions = emptyList(),
            currencyCode = "EUR",
            generationToken = " ",
            language = " ",
            maxSuggestions = 3,
        )

        // WHEN
        val built = buildUserPrompt(prompt)

        // THEN
        assertTrue(built.contains("Trip city: any city from the trip"))
        assertTrue(built.contains("User description: No additional description."))
        assertTrue(built.contains("Type options: Any types"))
        assertTrue(built.contains("Time of day options: Any time of day"))
        assertTrue(built.contains("Budget options: Any budget"))
        assertTrue(built.contains("Generation token: none"))
        assertTrue(built.contains("Response language: en"))
    }

    @Test
    fun given_fencedAndPlainContent_when_unwrapJson_then_returnsCleanJsonText() {
        // WHEN
        val fencedJson = unwrapJson(
            """
            ```json
            {"items":[]}
            ```
            """.trimIndent()
        )
        val fencedPlain = unwrapJson(
            """
            ```
            {"items":[]}
            ```
            """.trimIndent()
        )
        val plain = unwrapJson("  {\"items\":[]}  ")

        // THEN
        assertEquals("{\"items\":[]}", fencedJson)
        assertEquals("{\"items\":[]}", fencedPlain)
        assertEquals("{\"items\":[]}", plain)
    }

    @Test
    fun given_differentContentShapes_when_extractMessageContent_then_handlesAllBranches() {
        // GIVEN
        val arrayContent = buildJsonArray {
            add(buildJsonObject { put("text", "Hello ") })
            add(buildJsonObject { put("content", "World") })
            add(JsonPrimitive(42))
        }

        // WHEN
        val fromNull = extractMessageContent(null)
        val fromPrimitive = extractMessageContent(JsonPrimitive("  Hi  "))
        val fromArray = extractMessageContent(arrayContent)
        val fromObject = extractMessageContent(buildJsonObject { put("x", 1) })

        // THEN
        assertEquals("", fromNull)
        assertEquals("Hi", fromPrimitive)
        assertEquals("Hello World", fromArray)
        assertTrue(fromObject.contains("\"x\""))
    }

    @Test
    fun given_mixedItems_when_parseSuggestions_then_filtersAndNormalizesOutput() {
        // GIVEN
        val raw = """
            ```json
            {
              "items": [
                {
                  "title": "   ",
                  "place": "Any",
                  "description": "skip",
                  "typeLabel": "skip",
                  "durationLabel": "skip",
                  "budgetLabel": "skip",
                  "estimatedCost": 10
                },
                {
                  "title": "Louvre",
                  "place": " ",
                  "description": "Museum",
                  "typeLabel": "culture",
                  "durationLabel": "2h",
                  "budgetLabel": "medium",
                  "estimatedCost": -5
                },
                {
                  "title": "louvre",
                  "place": "Rue de Rivoli, Paris",
                  "description": "Duplicate by title",
                  "typeLabel": "culture",
                  "durationLabel": "3h",
                  "budgetLabel": "high",
                  "estimatedCost": 25.5
                },
                {
                  "title": "Colosseum",
                  "place": "Piazza del Colosseo, 1, Rome",
                  "description": null,
                  "typeLabel": null,
                  "durationLabel": null,
                  "budgetLabel": null,
                  "estimatedCost": null
                }
              ]
            }
            ```
        """.trimIndent()

        // WHEN
        val parsed = parseSuggestions(raw, maxSuggestions = 2)

        // THEN
        assertEquals(2, parsed.size)
        assertEquals("Louvre", parsed[0].title)
        assertNull(parsed[0].place)
        assertNull(parsed[0].estimatedCost)
        assertEquals("Colosseum", parsed[1].title)
    }

    private fun baseConfig(
        apiKey: String?,
        folderId: String?,
    ): AiConfig = AiConfig(
        provider = "yandex",
        yandexApiKey = apiKey,
        yandexFolderId = folderId,
        yandexModel = "yandexgpt/latest",
        requestTimeoutMillis = 10_000L,
        maxSuggestions = 5,
    )

    private fun prompt(): YandexTripSuggestionPrompt = YandexTripSuggestionPrompt(
        city = "Rome",
        description = "Museums",
        typeOptions = listOf("culture"),
        timeOfDayOptions = listOf("morning"),
        budgetOptions = listOf("medium"),
        currencyCode = "EUR",
        generationToken = "token",
        language = "en",
        maxSuggestions = 3,
    )

    private fun resolveModel(folderId: String, configuredModel: String): String {
        val method = YandexAiClient::class.java.getDeclaredMethod(
            "resolveModel",
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(YandexAiClient, folderId, configuredModel) as String
    }

    private fun buildUserPrompt(prompt: YandexTripSuggestionPrompt): String {
        val method = YandexAiClient::class.java.getDeclaredMethod(
            "buildUserPrompt",
            YandexTripSuggestionPrompt::class.java,
        )
        method.isAccessible = true
        return method.invoke(YandexAiClient, prompt) as String
    }

    private fun unwrapJson(raw: String): String {
        val method = YandexAiClient::class.java.getDeclaredMethod("unwrapJson", String::class.java)
        method.isAccessible = true
        return method.invoke(YandexAiClient, raw) as String
    }

    private fun extractMessageContent(contentNode: JsonElement?): String {
        val method = YandexAiClient::class.java.getDeclaredMethod(
            "extractMessageContent",
            JsonElement::class.java,
        )
        method.isAccessible = true
        return method.invoke(YandexAiClient, contentNode) as String
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSuggestions(raw: String, maxSuggestions: Int): List<nvk.cotrip.backend.db.AiSuggestionInput> {
        val method = YandexAiClient::class.java.getDeclaredMethod(
            "parseSuggestions",
            String::class.java,
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(YandexAiClient, raw, maxSuggestions) as List<nvk.cotrip.backend.db.AiSuggestionInput>
    }
}
