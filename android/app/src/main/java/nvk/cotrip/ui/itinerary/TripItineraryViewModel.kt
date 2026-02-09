package nvk.cotrip.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TripItineraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val api: CoTripApi,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripItinerary.ARG_TRIP_ID])

    private var allCities: List<String> = emptyList()
    private var currencySymbol: String = "€"

    private val _state = MutableStateFlow(
        TripItineraryState(
            tripId = tripId,
            dateRange = "",
            mode = ItineraryMode.Empty,
            days = emptyList(),
            cityPicker = null
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripItineraryEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadItinerary()
    }

    fun onEvent(event: TripItineraryEvent) {
        when (event) {
            TripItineraryEvent.OnBackClick -> appNavigator.popBackStack()
            TripItineraryEvent.OnRefresh -> loadItinerary()
            TripItineraryEvent.OnAddActivityClick -> appNavigator.navigate(
                Destination.CreateActivity(tripId)
            )

            TripItineraryEvent.OnDismissCityPicker -> _state.update { it.copy(cityPicker = null) }
            is TripItineraryEvent.OnActivityClick -> appNavigator.navigate(
                Destination.ActivityDetails(event.activityId)
            )

            is TripItineraryEvent.OnChooseCityClick -> openCityPicker(event.dayId)
            is TripItineraryEvent.OnCityQueryChange -> updateCityQuery(event.value)
            is TripItineraryEvent.OnCitySelected -> selectCity(event.city)
        }
    }

    private fun loadItinerary() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val trip = api.getTrip(tripId)
                    val itinerary = api.getItinerary(tripId).items
                    currencySymbol = currencySymbolFor(trip.currencyCode)
                    allCities =
                        itinerary.mapNotNull { it.city?.takeIf(String::isNotBlank) }.distinct()
                    ItineraryPayload(
                        dateRange = formatRange(trip.startDate, trip.endDate),
                        days = itinerary
                    )
                }
            }.onSuccess { payload ->
                val dayUis = payload.days.map { it.toUi(currencySymbol) }
                _state.update {
                    it.copy(
                        dateRange = payload.dateRange,
                        mode = if (dayUis.isEmpty()) ItineraryMode.Empty else ItineraryMode.Filled,
                        days = dayUis
                    )
                }
            }.onFailure {
                emitToast(R.string.common_error_message)
            }
        }
    }

    private fun openCityPicker(dayId: String) {
        val cities = allCities
        _state.update { st ->
            st.copy(
                cityPicker = CityPickerState(
                    dayId = dayId,
                    query = "",
                    allCities = cities,
                    filteredCities = cities
                )
            )
        }
    }

    private fun updateCityQuery(value: String) {
        _state.update { st ->
            val picker = st.cityPicker ?: return@update st
            val filtered = if (value.isBlank()) {
                picker.allCities
            } else {
                picker.allCities.filter { it.contains(value, ignoreCase = true) }
            }
            st.copy(cityPicker = picker.copy(query = value, filteredCities = filtered))
        }
    }

    private fun selectCity(city: String) {
        val picker = _state.value.cityPicker ?: return
        _state.update { it.copy(cityPicker = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.updateDay(picker.dayId, UpdateDayRequest(city = city))
                }
            }.onSuccess {
                _state.update { st ->
                    val days = st.days.map { d ->
                        if (d.id == picker.dayId) d.copy(city = city) else d
                    }
                    st.copy(days = days)
                }
            }.onFailure {
                emitToast(R.string.common_error_message)
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripItineraryEffect.ShowToastRes(resId)) }
    }

    private data class ItineraryPayload(
        val dateRange: String,
        val days: List<ItineraryDayDto>,
    )
}

private fun ItineraryDayDto.toUi(currencySymbol: String): ItineraryDayUi {
    val date = LocalDate.parse(date)
    val dateText = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date)
    val activities = activities.sortedBy { it.orderIndex }.map { it.toUi(currencySymbol) }
    return ItineraryDayUi(
        id = id,
        dayNumber = dayNumber,
        dateText = dateText,
        city = city,
        activities = activities,
    )
}

private fun ActivityDto.toUi(currencySymbol: String): ItineraryActivityUi {
    val priceText = costAmount?.let { formatCost(it, currencySymbol) }
    return ItineraryActivityUi(
        id = id,
        timeText = timeText.orEmpty(),
        title = title,
        subtitle = locationName,
        priceText = priceText,
    )
}

private fun formatRange(startDate: String, endDate: String): String {
    val start = LocalDate.parse(startDate)
    val end = LocalDate.parse(endDate)
    val locale = Locale.getDefault()
    val sameYear = start.year == end.year
    val startFormat = if (sameYear) "MMM d" else "MMM d, yyyy"
    val endFormat = "MMM d, yyyy"
    val startText = start.format(DateTimeFormatter.ofPattern(startFormat, locale))
    val endText = end.format(DateTimeFormatter.ofPattern(endFormat, locale))
    return "$startText – $endText"
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}

private fun formatCost(amount: Double, currencySymbol: String): String {
    val display = if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}
