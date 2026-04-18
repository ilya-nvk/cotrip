package nvk.cotrip.backend.ai

import nvk.cotrip.backend.db.AiSuggestionInput

data class AiSuggestionPostFilterRequest(
    val city: String?,
    val itineraryCities: List<String>,
    val typeOptions: List<String>,
    val timeOfDayOptions: List<String>,
    val budgetOptions: List<String>,
)

data class AiSuggestionPostFilterResult(
    val kept: List<AiSuggestionInput>,
    val generatedCount: Int,
    val rejectReasonCounts: Map<String, Int>,
) {
    val keptCount: Int get() = kept.size

    val topRejectReasons: List<String>
        get() = rejectReasonCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3)
            .map { it.key }
}

object AiSuggestionPostFilter {
    fun filter(
        request: AiSuggestionPostFilterRequest,
        suggestions: List<AiSuggestionInput>,
    ): AiSuggestionPostFilterResult {
        val rejectReasonCounts = linkedMapOf<String, Int>()
        val kept = mutableListOf<AiSuggestionInput>()
        val seenKeys = mutableSetOf<String>()

        suggestions.forEach { rawSuggestion ->
            val suggestion = rawSuggestion.normalized()
            val rejectReason = rejectReason(request, suggestion)
            if (rejectReason != null) {
                rejectReasonCounts[rejectReason] = rejectReasonCounts.getOrDefault(rejectReason, 0) + 1
                return@forEach
            }

            val dedupeKey = normalizeDedupKey(suggestion.title, suggestion.place)
            if (!seenKeys.add(dedupeKey)) {
                rejectReasonCounts["duplicate"] = rejectReasonCounts.getOrDefault("duplicate", 0) + 1
                return@forEach
            }

            kept += suggestion
        }

        return AiSuggestionPostFilterResult(
            kept = kept,
            generatedCount = suggestions.size,
            rejectReasonCounts = rejectReasonCounts.toMap(),
        )
    }

    private fun rejectReason(
        request: AiSuggestionPostFilterRequest,
        suggestion: AiSuggestionInput,
    ): String? {
        if (suggestion.title.isBlank()) return "off_scope"

        val normalizedText = normalizeForAiMatching(
            listOfNotNull(
                suggestion.title,
                suggestion.place,
                suggestion.description,
                suggestion.typeLabel,
                suggestion.durationLabel,
                suggestion.budgetLabel,
            ).joinToString(" ")
        )

        if (normalizedText.findFirstNormalizedPhrase(metaResponsePhrases) != null) {
            return "meta"
        }
        if (
            normalizedText.findFirstNormalizedPhrase(illegalGoodsPhrases) != null ||
            normalizedText.findFirstNormalizedPhrase(harmfulPhrases) != null
        ) {
            return "unsafe_content"
        }
        if (normalizedText.findFirstNormalizedPhrase(offScopeSuggestionPhrases) != null) {
            return "off_scope"
        }
        if (hasCityMismatch(request, suggestion, normalizedText)) {
            return "city_mismatch"
        }
        if (hasTypeMismatch(request, suggestion)) {
            return "type_mismatch"
        }
        if (hasTimeMismatch(request, normalizedText)) {
            return "time_mismatch"
        }
        if (hasBudgetMismatch(request, suggestion)) {
            return "budget_mismatch"
        }

        return null
    }

    private fun hasCityMismatch(
        request: AiSuggestionPostFilterRequest,
        suggestion: AiSuggestionInput,
        normalizedText: String,
    ): Boolean {
        val requestedCity = normalizeForAiMatching(request.city)
        if (requestedCity.isBlank()) return false

        val knownTripCities = request.itineraryCities
            .map(::normalizeForAiMatching)
            .filter { it.isNotBlank() }
            .toSet()
            .plus(requestedCity)

        return knownTripCities.any { knownCity ->
            knownCity != requestedCity &&
                (
                    normalizedText.containsNormalizedPhrase(knownCity) ||
                        normalizeForAiMatching(suggestion.place).containsNormalizedPhrase(knownCity)
                    )
        }
    }

    private fun hasTypeMismatch(
        request: AiSuggestionPostFilterRequest,
        suggestion: AiSuggestionInput,
    ): Boolean {
        if (request.typeOptions.isEmpty()) return false
        val allowedGroups = request.typeOptions.mapNotNull(::detectTypeGroup).toSet()
        if (allowedGroups.isEmpty()) return false
        val suggestionGroup = detectTypeGroup(suggestion.typeLabel) ?: return false
        return suggestionGroup !in allowedGroups
    }

    private fun hasTimeMismatch(
        request: AiSuggestionPostFilterRequest,
        normalizedText: String,
    ): Boolean {
        if (request.timeOfDayOptions.isEmpty()) return false
        val allowedGroups = request.timeOfDayOptions.mapNotNull(::detectTimeGroup).toSet()
        if (allowedGroups.isEmpty()) return false
        val suggestionGroup = detectTimeGroup(normalizedText) ?: return false
        return suggestionGroup !in allowedGroups
    }

    private fun hasBudgetMismatch(
        request: AiSuggestionPostFilterRequest,
        suggestion: AiSuggestionInput,
    ): Boolean {
        if (request.budgetOptions.isEmpty()) return false
        val allowedGroups = request.budgetOptions.mapNotNull(::detectBudgetGroup).toSet()
        if (allowedGroups.isEmpty()) return false
        val suggestionGroup = detectBudgetGroup(suggestion.budgetLabel) ?: return false
        return suggestionGroup !in allowedGroups
    }

    private fun AiSuggestionInput.normalized(): AiSuggestionInput {
        return copy(
            title = title.trim(),
            place = normalizeSuggestionField(place),
            description = normalizeSuggestionField(description),
            typeLabel = normalizeSuggestionField(typeLabel),
            durationLabel = normalizeSuggestionField(durationLabel),
            budgetLabel = normalizeSuggestionField(budgetLabel),
        )
    }

    private fun detectTypeGroup(value: String?): String? {
        val normalized = normalizeForAiMatching(value)
        if (normalized.isBlank()) return null
        return when {
            normalized.containsNormalizedPhrase("must see") ||
                normalized.containsNormalizedPhrase("landmark") ||
                normalized.containsNormalizedPhrase("iconic") ||
                normalized.containsNormalizedPhrase("обязательно к посещению") ||
                normalized.containsNormalizedPhrase("достопримеч") ->
                "must_see"

            normalized.containsNormalizedPhrase("food") ||
                normalized.containsNormalizedPhrase("restaurant") ||
                normalized.containsNormalizedPhrase("restaurants") ||
                normalized.containsNormalizedPhrase("cafe") ||
                normalized.containsNormalizedPhrase("cafes") ||
                normalized.containsNormalizedPhrase("market") ||
                normalized.containsNormalizedPhrase("еда") ||
                normalized.containsNormalizedPhrase("ресторан") ||
                normalized.containsNormalizedPhrase("кафе") ->
                "food"

            normalized.containsNormalizedPhrase("museum") ||
                normalized.containsNormalizedPhrase("museums") ||
                normalized.containsNormalizedPhrase("gallery") ||
                normalized.containsNormalizedPhrase("galleries") ||
                normalized.containsNormalizedPhrase("exhibit") ||
                normalized.containsNormalizedPhrase("музей") ||
                normalized.containsNormalizedPhrase("галере") ->
                "museums"

            normalized.containsNormalizedPhrase("night") ||
                normalized.containsNormalizedPhrase("nightlife") ||
                normalized.containsNormalizedPhrase("bar") ||
                normalized.containsNormalizedPhrase("club") ||
                normalized.containsNormalizedPhrase("ноч") ||
                normalized.containsNormalizedPhrase("бар") ||
                normalized.containsNormalizedPhrase("клуб") ->
                "night"

            normalized.containsNormalizedPhrase("nature") ||
                normalized.containsNormalizedPhrase("park") ||
                normalized.containsNormalizedPhrase("garden") ||
                normalized.containsNormalizedPhrase("beach") ||
                normalized.containsNormalizedPhrase("hike") ||
                normalized.containsNormalizedPhrase("trail") ||
                normalized.containsNormalizedPhrase("природ") ||
                normalized.containsNormalizedPhrase("парк") ||
                normalized.containsNormalizedPhrase("сад") ||
                normalized.containsNormalizedPhrase("пляж") ->
                "nature"

            normalized.containsNormalizedPhrase("budget") ||
                normalized.containsNormalizedPhrase("cheap") ||
                normalized.containsNormalizedPhrase("affordable") ||
                normalized.containsNormalizedPhrase("бюджет") ||
                normalized.containsNormalizedPhrase("дешев") ->
                "budget"

            normalized.containsNormalizedPhrase("random") ||
                normalized.containsNormalizedPhrase("surprise") ||
                normalized.containsNormalizedPhrase("offbeat") ||
                normalized.containsNormalizedPhrase("случайн") ||
                normalized.containsNormalizedPhrase("неожидан") ->
                "random"

            else -> null
        }
    }

    private fun detectTimeGroup(value: String?): String? {
        val normalized = normalizeForAiMatching(value)
        if (normalized.isBlank()) return null
        return when {
            normalized.containsNormalizedPhrase("morning") ||
                normalized.containsNormalizedPhrase("breakfast") ||
                normalized.containsNormalizedPhrase("brunch") ||
                normalized.containsNormalizedPhrase("sunrise") ||
                normalized.containsNormalizedPhrase("утро") ||
                normalized.containsNormalizedPhrase("завтрак") ||
                normalized.containsNormalizedPhrase("рассвет") ->
                "morning"

            normalized.containsNormalizedPhrase("afternoon") ||
                normalized.containsNormalizedPhrase("midday") ||
                normalized.containsNormalizedPhrase("lunch") ||
                normalized.containsNormalizedPhrase("daytime") ||
                normalized.containsNormalizedPhrase("день") ||
                normalized.containsNormalizedPhrase("обед") ->
                "afternoon"

            normalized.containsNormalizedPhrase("evening") ||
                normalized.containsNormalizedPhrase("night") ||
                normalized.containsNormalizedPhrase("sunset") ||
                normalized.containsNormalizedPhrase("dinner") ||
                normalized.containsNormalizedPhrase("late night") ||
                normalized.containsNormalizedPhrase("вечер") ||
                normalized.containsNormalizedPhrase("ноч") ||
                normalized.containsNormalizedPhrase("закат") ||
                normalized.containsNormalizedPhrase("ужин") ->
                "evening"

            else -> null
        }
    }

    private fun detectBudgetGroup(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        return when {
            raw == "€€€" -> "premium"
            raw == "€€" -> "mid"
            raw == "€" -> "budget"
            normalizeForAiMatching(raw).containsNormalizedPhrase("free") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("бесплат") ->
                "free"

            normalizeForAiMatching(raw).containsNormalizedPhrase("premium") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("luxury") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("expensive") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("премиум") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("дорог") ->
                "premium"

            normalizeForAiMatching(raw).containsNormalizedPhrase("mid range") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("moderate") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("medium") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("средн") ->
                "mid"

            normalizeForAiMatching(raw).containsNormalizedPhrase("budget") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("cheap") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("affordable") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("бюджет") ||
                normalizeForAiMatching(raw).containsNormalizedPhrase("дешев") ->
                "budget"

            else -> null
        }
    }
}
