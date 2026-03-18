package nvk.cotrip.ui.activity.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityDetailsScreenTest {
    @Test
    fun given_blankOrNullInput_when_normalizeActivityLink_then_returnsNull() {
        // GIVEN / WHEN / THEN
        assertNull(normalizeActivityLink(null))
        assertNull(normalizeActivityLink(""))
        assertNull(normalizeActivityLink("   "))
    }

    @Test
    fun given_validInputWithWhitespace_when_normalizeActivityLink_then_trimsAndReturns() {
        // GIVEN / WHEN / THEN
        assertEquals("https://example.test", normalizeActivityLink("  https://example.test  "))
    }
}
