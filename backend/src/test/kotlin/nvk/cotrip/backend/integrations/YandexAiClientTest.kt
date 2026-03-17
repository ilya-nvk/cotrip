package nvk.cotrip.backend.integrations

import kotlinx.coroutines.runBlocking
import nvk.cotrip.backend.config.AiConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith
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
}
