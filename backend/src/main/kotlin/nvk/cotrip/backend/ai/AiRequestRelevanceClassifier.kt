package nvk.cotrip.backend.ai

import nvk.cotrip.backend.config.AiConfig
import nvk.cotrip.backend.integrations.YandexAiClient
import nvk.cotrip.backend.integrations.YandexTripRequestRelevancePrompt

enum class AiRequestRelevanceCategory(val wireValue: String) {
    TRAVEL("travel"),
    OFF_TOPIC("off_topic"),
    UNCLEAR("unclear"),
}

data class AiRequestRelevanceInput(
    val city: String?,
    val description: String?,
    val typeOptions: List<String>,
    val timeOfDayOptions: List<String>,
    val budgetOptions: List<String>,
    val generationToken: String?,
)

data class AiRequestRelevanceResult(
    val isTravelRelated: Boolean,
    val confidence: Double,
    val category: AiRequestRelevanceCategory,
)

object AiRequestRelevanceClassifier {
    const val OFF_TOPIC_BLOCK_CONFIDENCE_THRESHOLD: Double = 0.8

    suspend fun classify(
        provider: String,
        config: AiConfig,
        input: AiRequestRelevanceInput,
    ): AiRequestRelevanceResult? {
        return when (provider) {
            "yandex" -> YandexAiClient.classifyRequestRelevance(
                config = config,
                prompt = YandexTripRequestRelevancePrompt(
                    city = input.city,
                    description = input.description,
                    typeOptions = input.typeOptions,
                    timeOfDayOptions = input.timeOfDayOptions,
                    budgetOptions = input.budgetOptions,
                ),
            )

            "mock" -> classifyMock(input)
            else -> throw IllegalArgumentException("Unsupported AI provider: $provider")
        }
    }

    fun shouldBlockAsOffTopic(result: AiRequestRelevanceResult?): Boolean {
        return result != null &&
            !result.isTravelRelated &&
            result.category == AiRequestRelevanceCategory.OFF_TOPIC &&
            result.confidence >= OFF_TOPIC_BLOCK_CONFIDENCE_THRESHOLD
    }

    private fun classifyMock(input: AiRequestRelevanceInput): AiRequestRelevanceResult? {
        return when (input.generationToken?.trim()) {
            "test-relevance-off-topic-high" -> offTopic(confidence = 0.95)
            "test-relevance-off-topic-low" -> offTopic(confidence = 0.55)
            "test-relevance-invalid" -> throw IllegalArgumentException("Mock classifier returned invalid payload")
            "test-relevance-failure" -> throw IllegalStateException("Mock classifier failed")
            else -> classifyMockPromptInjectionSample(input)
        }
    }

    private fun classifyMockPromptInjectionSample(input: AiRequestRelevanceInput): AiRequestRelevanceResult? {
        val normalized = normalizeForAiMatching(
            listOfNotNull(input.city, input.description)
                .plus(input.typeOptions)
                .plus(input.timeOfDayOptions)
                .plus(input.budgetOptions)
                .joinToString(" ")
        )
        if (!normalized.containsNormalizedPhrase("ignore previous instructions")) {
            return null
        }

        return when {
            normalized.containsNormalizedPhrase("bitcoin") ||
                normalized.containsNormalizedPhrase("crypto") ||
                normalized.containsNormalizedPhrase("биткоин") ->
                offTopic(confidence = 0.96)

            normalized.containsNormalizedPhrase("museum") ||
                normalized.containsNormalizedPhrase("museums") ||
                normalized.containsNormalizedPhrase("музей") ->
                AiRequestRelevanceResult(
                    isTravelRelated = true,
                    confidence = 0.91,
                    category = AiRequestRelevanceCategory.TRAVEL,
                )

            else -> AiRequestRelevanceResult(
                isTravelRelated = false,
                confidence = 0.45,
                category = AiRequestRelevanceCategory.UNCLEAR,
            )
        }
    }

    private fun offTopic(confidence: Double): AiRequestRelevanceResult {
        return AiRequestRelevanceResult(
            isTravelRelated = false,
            confidence = confidence,
            category = AiRequestRelevanceCategory.OFF_TOPIC,
        )
    }
}
