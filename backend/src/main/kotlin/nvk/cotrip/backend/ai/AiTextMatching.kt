package nvk.cotrip.backend.ai

import java.text.Normalizer
import java.util.Locale

internal fun normalizeForAiMatching(value: String?): String {
    if (value == null) return ""
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")
}

internal fun String.containsNormalizedPhrase(phrase: String): Boolean {
    if (this.isBlank()) return false
    val normalizedPhrase = normalizeForAiMatching(phrase)
    if (normalizedPhrase.isBlank()) return false
    return " $this ".contains(" $normalizedPhrase ")
}

internal fun String.findFirstNormalizedPhrase(phrases: Iterable<String>): String? {
    return phrases.firstOrNull { containsNormalizedPhrase(it) }
}

internal fun normalizeSuggestionField(value: String?): String? {
    return value?.trim()?.ifBlank { null }
}

internal fun normalizeDedupKey(vararg parts: String?): String {
    return parts.joinToString("|") { normalizeForAiMatching(it) }
}
