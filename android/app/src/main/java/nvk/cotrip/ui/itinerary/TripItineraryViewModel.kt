package nvk.cotrip.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
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
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripItinerary.ARG_TRIP_ID])
    private val requireCitySelection: Boolean =
        savedStateHandle[Destination.TripItinerary.ARG_REQUIRE_CITIES] ?: false
    private val isCreationFlow: Boolean =
        savedStateHandle[Destination.TripItinerary.ARG_CREATION_FLOW] ?: false

    private var allCities: List<CitySuggestionUi> = emptyList()
    private var currencySymbol: String = "€"
    private var citySearchJob: Job? = null
    private var isCancellingCreation: Boolean = false

    private val _state = MutableStateFlow(
        TripItineraryState(
            tripId = tripId,
            dateRange = "",
            mode = ItineraryMode.Empty,
            days = emptyList(),
            cityPicker = null,
            isReordering = false,
            isCitySelectionRequired = requireCitySelection,
            pendingCitySelectionCount = 0,
            isRefreshing = false,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripItineraryEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeData()
        refreshItinerary(isUserRefresh = false)
    }

    fun onEvent(event: TripItineraryEvent) {
        when (event) {
            TripItineraryEvent.OnBackClick -> {
                if (isCreationFlow) {
                    cancelTripCreation()
                    return
                }
                if (_state.value.isCitySelectionRequired && _state.value.pendingCitySelectionCount > 0) {
                    emitToast(R.string.itinerary_city_setup_required_toast)
                } else {
                    appNavigator.popBackStack()
                }
            }
            TripItineraryEvent.OnCompleteRequiredCitySelection -> completeRequiredCitySelection()
            TripItineraryEvent.OnAutoRefresh -> refreshItinerary(isUserRefresh = false)
            TripItineraryEvent.OnUserRefresh -> refreshItinerary(isUserRefresh = true)
            TripItineraryEvent.OnToggleReorder -> toggleReorder()
            TripItineraryEvent.OnAddActivityClick -> appNavigator.navigate(
                Destination.CreateActivity(tripId)
            )

            TripItineraryEvent.OnDismissCityPicker -> {
                citySearchJob?.cancel()
                _state.update { it.copy(cityPicker = null) }
            }
            is TripItineraryEvent.OnActivityClick -> appNavigator.navigate(
                Destination.ActivityDetails(event.activityId)
            )

            is TripItineraryEvent.OnChooseCityClick -> openCityPicker(event.dayId)
            is TripItineraryEvent.OnCityQueryChange -> updateCityQuery(event.value)
            is TripItineraryEvent.OnCitySelected -> selectCity(event.city)
            is TripItineraryEvent.OnReorderMove ->
                reorderInState(event.dayId, event.fromIndex, event.toIndex)
            is TripItineraryEvent.OnReorderCommit ->
                commitReorder(event.dayId)
        }
    }

    private fun cancelTripCreation() {
        if (!isCreationFlow || isCancellingCreation) return
        isCancellingCreation = true
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.deleteTrip(tripId)
                }
            }) {
                is ApiResult.Success -> appNavigator.popBackStack()
                is ApiResult.Failure -> emitToast(uiErrorMapper.messageRes(result))
            }
            _state.update { it.copy(isRefreshing = false) }
            isCancellingCreation = false
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                tripRepository.observeTrip(tripId),
                itineraryRepository.observeItinerary(tripId)
            ) { trip, itinerary -> Pair(trip, itinerary) }
                .collect { (trip, itinerary) ->
                    if (trip != null) {
                        currencySymbol = currencySymbolFor(trip.currencyCode)
                    }
                    allCities = collectTripCities(itinerary)
                    val pendingCitySelectionCount = itinerary.count {
                        !it.isOutOfRange && it.city.isNullOrBlank()
                    }

                    val dayUis = itinerary.map { it.toUi(currencySymbol) }
                    val dateRange = trip?.let { formatRange(it.startDate, it.endDate) }
                        ?: _state.value.dateRange
                    _state.update {
                        val picker = it.cityPicker
                        val updatedPicker = if (picker != null && picker.query.isBlank()) {
                            picker.copy(
                                localSuggestions = allCities,
                                suggestions = allCities,
                                isSearching = false,
                            )
                        } else {
                            picker
                        }
                        it.copy(
                            dateRange = dateRange,
                            mode = if (dayUis.isEmpty()) ItineraryMode.Empty else ItineraryMode.Filled,
                            days = dayUis,
                            cityPicker = updatedPicker,
                            pendingCitySelectionCount = if (requireCitySelection) pendingCitySelectionCount else 0,
                        )
                    }
                }
        }
    }

    private fun refreshItinerary(isUserRefresh: Boolean) {
        viewModelScope.launch {
            if (isUserRefresh) {
                _state.update { it.copy(isRefreshing = true) }
            }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.getTrip(tripId)
                    itineraryRepository.refreshItinerary(tripId)
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> emitToast(uiErrorMapper.messageRes(result))
            }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private fun openCityPicker(dayId: String) {
        val selectedDay = _state.value.days.firstOrNull { it.id == dayId } ?: return
        val cities = allCities
        _state.update { st ->
            st.copy(
                cityPicker = CityPickerState(
                    dayId = selectedDay.id,
                    dayNumber = selectedDay.dayNumber,
                    dayDate = selectedDay.dateIso,
                    query = "",
                    localSuggestions = cities,
                    suggestions = cities,
                    isSearching = false,
                )
            )
        }
    }

    private fun toggleReorder() {
        if (_state.value.isCitySelectionRequired) return
        _state.update { it.copy(isReordering = !it.isReordering) }
    }

    private fun reorderInState(dayId: String, fromIndex: Int, toIndex: Int) {
        val current = _state.value
        val dayIndex = current.days.indexOfFirst { it.id == dayId }
        if (dayIndex == -1) return
        val day = current.days[dayIndex]
        if (fromIndex !in day.activities.indices) return
        val targetIndex = toIndex.coerceIn(0, day.activities.lastIndex)
        if (targetIndex == fromIndex) return
        val moved = day.activities.toMutableList().apply {
            add(targetIndex, removeAt(fromIndex))
        }

        val updatedDays = current.days.toMutableList().apply {
            set(dayIndex, day.copy(activities = moved))
        }
        _state.update { it.copy(days = updatedDays) }
    }

    private fun commitReorder(dayId: String) {
        val day = _state.value.days.firstOrNull { it.id == dayId } ?: return
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    itineraryRepository.reorderActivities(
                        dayId = dayId,
                        orderedIds = day.activities.map { it.id }
                    )
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    emitToast(uiErrorMapper.messageRes(result))
                    refreshItinerary(isUserRefresh = false)
                }
            }
        }
    }

    private fun updateCityQuery(value: String) {
        val query = value.trim()
        _state.value.cityPicker ?: return
        citySearchJob?.cancel()

        if (query.isBlank()) {
            _state.update { st ->
                val current = st.cityPicker ?: return@update st
                st.copy(
                    cityPicker = current.copy(
                        query = "",
                        suggestions = current.localSuggestions,
                        isSearching = false,
                    )
                )
            }
            return
        }

        _state.update { st ->
            val current = st.cityPicker ?: return@update st
            st.copy(
                cityPicker = current.copy(
                    query = query,
                    suggestions = emptyList(),
                    isSearching = true,
                )
            )
        }

        citySearchJob = viewModelScope.launch {
            delay(300)
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    itineraryRepository.searchCities(tripId = tripId, query = query, limit = 8)
                }
            }) {
                is ApiResult.Success -> {
                    val suggestions = result.data.map {
                        CitySuggestionUi(
                            name = it.name,
                            providerId = it.providerId,
                            lat = it.lat,
                            lon = it.lon,
                            fullText = it.fullText,
                        )
                    }
                    _state.update { st ->
                        val current = st.cityPicker ?: return@update st
                        if (current.query != query) {
                            return@update st
                        }
                        st.copy(
                            cityPicker = current.copy(
                                suggestions = suggestions,
                                isSearching = false,
                            )
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { st ->
                        val current = st.cityPicker ?: return@update st
                        if (current.query != query) {
                            return@update st
                        }
                        val fallback = current.localSuggestions.filter {
                            it.name.contains(query, ignoreCase = true)
                        }
                        st.copy(
                            cityPicker = current.copy(
                                suggestions = fallback,
                                isSearching = false,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun selectCity(city: CitySuggestionUi) {
        val picker = _state.value.cityPicker ?: return
        val selectedCityName = city.fullText?.trim().takeUnless { it.isNullOrBlank() } ?: city.name
        citySearchJob?.cancel()
        _state.update { it.copy(cityPicker = null) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    itineraryRepository.updateDay(
                        dayId = picker.dayId,
                        request = UpdateDayRequest(
                            city = selectedCityName,
                            cityProviderId = city.providerId,
                            cityLat = city.lat,
                            cityLon = city.lon,
                        )
                    )
                }
            }) {
                is ApiResult.Success -> {
                    _state.update { st ->
                        val days = st.days.map { d ->
                            if (d.id == picker.dayId) {
                                d.copy(city = selectedCityName)
                            } else {
                                d
                            }
                        }
                        st.copy(
                            days = days,
                            pendingCitySelectionCount = if (st.isCitySelectionRequired) {
                                days.count { it.city.isNullOrBlank() }
                            } else {
                                st.pendingCitySelectionCount
                            }
                        )
                    }
                    withContext(Dispatchers.IO) {
                        runCatching { itineraryRepository.refreshItinerary(tripId) }
                    }
                }

                is ApiResult.Failure -> {
                    val appliedOnServer = withContext(Dispatchers.IO) {
                        runCatching {
                            itineraryRepository.refreshItinerary(tripId).any { day ->
                                day.id == picker.dayId &&
                                    day.city?.trim()?.equals(selectedCityName.trim(), ignoreCase = true) == true
                            }
                        }.getOrDefault(false)
                    }

                    if (!appliedOnServer) {
                        emitToast(uiErrorMapper.messageRes(result))
                    }
                }
            }
        }
    }

    private fun completeRequiredCitySelection() {
        val current = _state.value
        if (!current.isCitySelectionRequired) return
        if (current.pendingCitySelectionCount > 0) {
            emitToast(R.string.itinerary_city_setup_required_toast)
            return
        }
        appNavigator.navigate(Destination.TripDetails(tripId)) {
            popUpTo(Destination.Trips.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripItineraryEffect.ShowToastRes(resId)) }
    }
}

private fun collectTripCities(days: List<ItineraryDayDto>): List<CitySuggestionUi> {
    val byName = linkedMapOf<String, CitySuggestionUi>()
    for (day in days) {
        val cityName = day.city?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val cityLat = day.cityLat ?: continue
        val cityLon = day.cityLon ?: continue
        val key = cityName.lowercase(Locale.getDefault())
        val candidate = CitySuggestionUi(
            name = cityName,
            providerId = day.cityProviderId,
            lat = cityLat,
            lon = cityLon,
            fullText = cityName,
        )
        val current = byName[key]
        byName[key] = if (current == null || (current.providerId == null && candidate.providerId != null)) {
            candidate
        } else {
            current
        }
    }
    return byName.values.toList()
}

private fun ItineraryDayDto.toUi(currencySymbol: String): ItineraryDayUi {
    val date = LocalDate.parse(date)
    val dateText = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date)
    val activities = activities.sortedBy { it.orderIndex }.map { it.toUi(currencySymbol) }
    return ItineraryDayUi(
        id = id,
        dayNumber = dayNumber,
        dateIso = this.date,
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
