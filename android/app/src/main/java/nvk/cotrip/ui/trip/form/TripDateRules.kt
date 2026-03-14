package nvk.cotrip.ui.trip.form

import java.time.LocalDate

internal object TripDateRules {
    fun minStartDate(today: LocalDate = LocalDate.now()): LocalDate = today

    fun maxStartDate(today: LocalDate = LocalDate.now()): LocalDate = today.plusYears(1)

    fun maxEndDateFor(startDate: LocalDate): LocalDate = startDate.plusMonths(6)

    fun isStartDateAllowed(
        startDate: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        return !startDate.isBefore(minStartDate(today)) && !startDate.isAfter(maxStartDate(today))
    }

    fun isEndDateAllowed(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Boolean {
        return !endDate.isBefore(startDate) && !endDate.isAfter(maxEndDateFor(startDate))
    }
}

