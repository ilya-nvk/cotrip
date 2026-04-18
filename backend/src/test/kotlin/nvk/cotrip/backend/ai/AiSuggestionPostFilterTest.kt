package nvk.cotrip.backend.ai

import nvk.cotrip.backend.db.AiSuggestionInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiSuggestionPostFilterTest {

    @Test
    fun given_mixedSafeUnsafeMetaAndOffScopeSuggestions_when_filter_then_keepsOnlyRelevantOnes() {
        val result = AiSuggestionPostFilter.filter(
            request = request(),
            suggestions = listOf(
                suggestion(
                    title = "Vatican Museums quiet start",
                    place = "Viale Vaticano 100, Rome",
                    description = "Morning museum route with a coffee stop.",
                    typeLabel = "Museums",
                    budgetLabel = "€€",
                ),
                suggestion(
                    title = "As an AI, compare hotel prices first",
                    description = "Use Google Maps and decide later.",
                    typeLabel = "Random",
                    budgetLabel = "€",
                ),
                suggestion(
                    title = "Buy cocaine near the station",
                    description = "Illegal nightlife plan.",
                    typeLabel = "Night",
                    budgetLabel = "€€€",
                ),
                suggestion(
                    title = "Book a hotel and airport transfer",
                    description = "General planning advice.",
                    typeLabel = "Must-see",
                    budgetLabel = "€€",
                ),
            ),
        )

        assertEquals(1, result.kept.size)
        assertEquals("Vatican Museums quiet start", result.kept.single().title)
        assertEquals(1, result.rejectReasonCounts["meta"])
        assertEquals(1, result.rejectReasonCounts["unsafe_content"])
        assertEquals(1, result.rejectReasonCounts["off_scope"])
    }

    @Test
    fun given_cityMismatch_when_filter_then_rejectsSuggestion() {
        val result = AiSuggestionPostFilter.filter(
            request = request(),
            suggestions = listOf(
                suggestion(
                    title = "Florence sunset walk",
                    place = "Old town, Florence",
                    description = "Evening walk in Florence.",
                    typeLabel = "Must-see",
                    budgetLabel = "Free",
                ),
            ),
        )

        assertTrue(result.kept.isEmpty())
        assertEquals(1, result.rejectReasonCounts["city_mismatch"])
    }

    @Test
    fun given_explicitFilterConflicts_when_filter_then_rejectsByTypeTimeAndBudget() {
        val result = AiSuggestionPostFilter.filter(
            request = request(),
            suggestions = listOf(
                suggestion(
                    title = "Street food tasting",
                    place = "Via Roma 1, Rome",
                    description = "Food-focused route with local snacks.",
                    typeLabel = "Food",
                    budgetLabel = "€€",
                ),
                suggestion(
                    title = "Evening museum entry",
                    place = "Museum street 5, Rome",
                    description = "Evening museum experience.",
                    typeLabel = "Museums",
                    budgetLabel = "€€",
                ),
                suggestion(
                    title = "Premium museum tasting",
                    place = "Culture lane 7, Rome",
                    description = "Morning museum plan with luxury extras.",
                    typeLabel = "Museums",
                    budgetLabel = "€€€",
                ),
            ),
        )

        assertTrue(result.kept.isEmpty())
        assertEquals(1, result.rejectReasonCounts["type_mismatch"])
        assertEquals(1, result.rejectReasonCounts["time_mismatch"])
        assertEquals(1, result.rejectReasonCounts["budget_mismatch"])
    }

    @Test
    fun given_duplicateTitleAndPlace_when_filter_then_keepsFirstOnly() {
        val result = AiSuggestionPostFilter.filter(
            request = request(),
            suggestions = listOf(
                suggestion(
                    title = "Vatican Museums quiet start",
                    place = "Viale Vaticano 100, Rome",
                    description = "Morning museum route.",
                    typeLabel = "Museums",
                    budgetLabel = "€€",
                ),
                suggestion(
                    title = "  vatican museums quiet start ",
                    place = "Viale Vaticano 100, Rome  ",
                    description = "Duplicate wording with different spacing.",
                    typeLabel = "Museums",
                    budgetLabel = "€€",
                ),
            ),
        )

        assertEquals(1, result.kept.size)
        assertEquals(1, result.rejectReasonCounts["duplicate"])
    }

    private fun request(): AiSuggestionPostFilterRequest = AiSuggestionPostFilterRequest(
        city = "Rome",
        itineraryCities = listOf("Rome", "Florence"),
        typeOptions = listOf("Museums"),
        timeOfDayOptions = listOf("Morning"),
        budgetOptions = listOf("€€"),
    )

    private fun suggestion(
        title: String,
        place: String? = null,
        description: String? = null,
        typeLabel: String? = null,
        budgetLabel: String? = null,
    ): AiSuggestionInput = AiSuggestionInput(
        title = title,
        place = place,
        description = description,
        typeLabel = typeLabel,
        durationLabel = "2-3 hours",
        budgetLabel = budgetLabel,
        estimatedCost = 25.0,
    )
}
