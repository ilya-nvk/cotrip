package nvk.cotrip.ui.trip.form

import java.time.LocalDate

data class TripFormState(
    val isLoading: Boolean = false,
    val coverUri: String? = null,
    val name: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val description: String = "",
    val currency: TripCurrency = TripCurrency.EUR,
    val availableCurrencies: List<TripCurrency> = TripCurrency.entries,
    val canSubmit: Boolean = false,
)