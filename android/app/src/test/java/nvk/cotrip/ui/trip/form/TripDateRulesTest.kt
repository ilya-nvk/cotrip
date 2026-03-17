package nvk.cotrip.ui.trip.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripDateRulesTest {
    @Test
    fun given_today_when_isStartDateAllowed_then_checksRangeFromTodayToPlusOneYear() {
        // GIVEN
        val today = LocalDate.of(2026, 3, 16)

        // WHEN / THEN
        assertTrue(TripDateRules.isStartDateAllowed(today, today))
        assertTrue(TripDateRules.isStartDateAllowed(today.plusYears(1), today))
        assertFalse(TripDateRules.isStartDateAllowed(today.minusDays(1), today))
        assertFalse(TripDateRules.isStartDateAllowed(today.plusYears(1).plusDays(1), today))
    }

    @Test
    fun given_startAndEnd_when_isEndDateAllowed_then_checksStartAndSixMonthsWindow() {
        // GIVEN
        val start = LocalDate.of(2026, 3, 16)
        val maxEnd = start.plusMonths(6)

        // WHEN / THEN
        assertTrue(TripDateRules.isEndDateAllowed(start, start))
        assertTrue(TripDateRules.isEndDateAllowed(start, maxEnd))
        assertFalse(TripDateRules.isEndDateAllowed(start, start.minusDays(1)))
        assertFalse(TripDateRules.isEndDateAllowed(start, maxEnd.plusDays(1)))
    }

    @Test
    fun given_today_when_minAndMaxStartDate_then_areDerivedFromToday() {
        // GIVEN
        val today = LocalDate.of(2026, 3, 16)

        // WHEN / THEN
        assertEquals(today, TripDateRules.minStartDate(today))
        assertEquals(today.plusYears(1), TripDateRules.maxStartDate(today))
    }
}
