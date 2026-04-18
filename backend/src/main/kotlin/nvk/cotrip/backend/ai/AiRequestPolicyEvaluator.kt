package nvk.cotrip.backend.ai

enum class AiRequestPolicyViolationCategory(val wireValue: String) {
    ILLEGAL_GOODS("illegal_goods"),
    HARMFUL("harmful"),
    OFF_TOPIC("off_topic"),
}

data class AiRequestPolicyDecision(
    val isAllowed: Boolean,
    val category: AiRequestPolicyViolationCategory? = null,
)

object AiRequestPolicyEvaluator {
    fun evaluate(
        city: String?,
        description: String?,
        typeOptions: List<String>,
        timeOfDayOptions: List<String>,
        budgetOptions: List<String>,
    ): AiRequestPolicyDecision {
        val normalized = normalizeForAiMatching(
            listOfNotNull(city, description)
                .plus(typeOptions)
                .plus(timeOfDayOptions)
                .plus(budgetOptions)
                .joinToString(" ")
        )
        if (normalized.isBlank()) {
            return AiRequestPolicyDecision(isAllowed = true)
        }

        return when {
            normalized.findFirstNormalizedPhrase(illegalGoodsPhrases) != null ->
                AiRequestPolicyDecision(isAllowed = false, category = AiRequestPolicyViolationCategory.ILLEGAL_GOODS)

            normalized.findFirstNormalizedPhrase(harmfulPhrases) != null ->
                AiRequestPolicyDecision(isAllowed = false, category = AiRequestPolicyViolationCategory.HARMFUL)

            else -> AiRequestPolicyDecision(isAllowed = true)
        }
    }
}
