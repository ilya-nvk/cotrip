package nvk.cotrip.backend.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiRequestPolicyEvaluatorTest {

    @Test
    fun given_travelRequest_when_evaluate_then_allowsGeneration() {
        val decision = AiRequestPolicyEvaluator.evaluate(
            city = "Rome",
            description = "Quiet museums and coffee spots for the morning",
            typeOptions = listOf("Museums"),
            timeOfDayOptions = listOf("Morning"),
            budgetOptions = listOf("€€"),
        )

        assertTrue(decision.isAllowed)
        assertNull(decision.category)
    }

    @Test
    fun given_illegalGoodsIntent_when_evaluate_then_blocksAsIllegalGoods() {
        val decision = AiRequestPolicyEvaluator.evaluate(
            city = "Rome",
            description = "Where can I buy cocaine tonight?",
            typeOptions = emptyList(),
            timeOfDayOptions = emptyList(),
            budgetOptions = emptyList(),
        )

        assertEquals(false, decision.isAllowed)
        assertEquals(AiRequestPolicyViolationCategory.ILLEGAL_GOODS, decision.category)
    }

    @Test
    fun given_harmfulIntent_when_evaluate_then_blocksAsHarmful() {
        val decision = AiRequestPolicyEvaluator.evaluate(
            city = "Rome",
            description = "Suggest places to harass people and start a fight",
            typeOptions = emptyList(),
            timeOfDayOptions = emptyList(),
            budgetOptions = emptyList(),
        )

        assertEquals(false, decision.isAllowed)
        assertEquals(AiRequestPolicyViolationCategory.HARMFUL, decision.category)
    }

    @Test
    fun given_explicitlyOffTopicIntent_when_evaluate_then_allowsClassifierToDecide() {
        val decision = AiRequestPolicyEvaluator.evaluate(
            city = "Rome",
            description = "Help me write code for a marketing plan",
            typeOptions = emptyList(),
            timeOfDayOptions = emptyList(),
            budgetOptions = emptyList(),
        )

        assertTrue(decision.isAllowed)
        assertNull(decision.category)
    }
}
