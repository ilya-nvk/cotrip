package nvk.cotrip.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.network.dto.cityDisplayLabel
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.PendingTripCreationStore
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.TextInputLimits
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import nvk.cotrip.util.AppLogger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TripItineraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val pendingTripCreationStore: PendingTripCreationStore,
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
    private var hasRetriedInitialCreationRefresh: Boolean = false

    private val _state = MutableStateFlow(
        TripItineraryState(
            tripId = tripId,
            dateRange = "",
            isPastTrip = false,
            mode = ItineraryMode.Empty,
            days = emptyList(),
            cityPicker = null,
            isCitySelectionRequired = requireCitySelection,
            pendingCitySelectionCount = 0,
            isRefreshing = false,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripItineraryEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        if (isCreationFlow) {
            viewModelScope.launch {
                runCatching { pendingTripCreationStore.setPendingTripId(tripId) }
                    .onFailure { AppLogger.w(TAG, "Failed to persist pending tripId=$tripId", it) }
            }
        }
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
                    _state.update { it.copy(inlineErrorRes = R.string.itinerary_city_setup_required_toast) }
                } else {
                    appNavigator.popBackStack()
                }
            }
            TripItineraryEvent.OnCompleteRequiredCitySelection -> completeRequiredCitySelection()
            TripItineraryEvent.OnAutoRefresh -> refreshItinerary(isUserRefresh = false)
            TripItineraryEvent.OnUserRefresh -> refreshItinerary(isUserRefresh = true)
            TripItineraryEvent.OnAddActivityClick -> {
                if (_state.value.isPastTrip) return
                appNavigator.navigate(
                    Destination.CreateActivity(tripId)
                )
            }

            TripItineraryEvent.OnDismissCityPicker -> {
                citySearchJob?.cancel()
                _state.update { it.copy(cityPicker = null) }
            }
            is TripItineraryEvent.OnActivityClick -> appNavigator.navigate(
                Destination.ActivityDetails(event.activityId)
            )

            is TripItineraryEvent.OnChooseCityClick -> {
                if (_state.value.isPastTrip) return
                _state.update { it.copy(inlineErrorRes = null) }
                openCityPicker(event.dayId)
            }

            is TripItineraryEvent.OnCityQueryChange -> {
                if (_state.value.isPastTrip) return
                updateCityQuery(event.value)
            }

            is TripItineraryEvent.OnCitySelected -> {
                if (_state.value.isPastTrip) return
                selectCity(event.city)
            }

            is TripItineraryEvent.OnCitySelectedForFollowingDays -> {
                if (_state.value.isPastTrip) return
                selectCityForFollowingDays(event.city)
            }
        }
    }

    private fun cancelTripCreation() {
        if (!isCreationFlow || isCancellingCreation) return
        isCancellingCreation = true
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            AppLogger.i(TAG, "cancelTripCreation started for tripId=$tripId")
            when (val result = apiCaller.call {
                tripRepository.deleteTrip(tripId)
            }) {
                is ApiResult.Success -> {
                    AppLogger.i(TAG, "cancelTripCreation succeeded for tripId=$tripId")
                    clearPendingCreationTrip()
                    appNavigator.popBackStack()
                }
                is ApiResult.Failure -> {
                    if (result.httpCode == 404) {
                        AppLogger.i(TAG, "cancelTripCreation got 404 for tripId=$tripId, closing screen")
                        clearPendingCreationTrip()
                        appNavigator.popBackStack()
                    } else {
                        AppLogger.w(
                            TAG,
                            "cancelTripCreation failed for tripId=$tripId code=${result.httpCode} apiCode=${result.error?.code.orEmpty()}",
                            result.cause
                        )
                        emitToast(uiErrorMapper.messageRes(result))
                    }
                }
            }
            _state.update { it.copy(isRefreshing = false) }
            isCancellingCreation = false
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                tripRepository.getTrip(tripId),
                itineraryRepository.observeItinerary(tripId)
            ) { trip, itinerary -> Pair(trip, itinerary) }
                .collect { (trip, itinerary) ->
                    currencySymbol = currencySymbolFor(trip.currencyCode)
                    allCities = collectTripCities(itinerary)
                    val isPastTrip = runCatching {
                        LocalDate.parse(trip.endDate).isBefore(LocalDate.now())
                    }.getOrDefault(false)
                    val pendingCitySelectionCount = itinerary.count {
                        !it.isOutOfRange && it.city.isNullOrBlank()
                    }

                    val dayUis = itinerary.map { it.toUi(currencySymbol) }
                    val dateRange = formatRange(trip.startDate, trip.endDate)
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
                            isPastTrip = isPastTrip,
                            mode = if (dayUis.isEmpty()) ItineraryMode.Empty else ItineraryMode.Filled,
                            days = dayUis,
                            cityPicker = updatedPicker,
                            pendingCitySelectionCount = if (requireCitySelection && !isPastTrip) {
                                pendingCitySelectionCount
                            } else {
                                0
                            },
                            inlineErrorRes = if (
                                requireCitySelection &&
                                !isPastTrip &&
                                pendingCitySelectionCount > 0
                            ) {
                                it.inlineErrorRes
                            } else {
                                null
                            },
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
                tripRepository.getTrip(tripId).first()
                itineraryRepository.refreshItinerary(tripId).getOrThrow()
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    val shouldSuppressInitialError = !isUserRefresh &&
                        isCreationFlow &&
                        !hasRetriedInitialCreationRefresh
                    AppLogger.w(
                        TAG,
                        "refreshItinerary failed tripId=$tripId userRefresh=$isUserRefresh code=${result.httpCode} apiCode=${result.error?.code.orEmpty()} suppress=$shouldSuppressInitialError",
                        result.cause
                    )
                    if (shouldSuppressInitialError) {
                        hasRetriedInitialCreationRefresh = true
                        delay(400)
                        refreshItinerary(isUserRefresh = false)
                    } else {
                        if (isUserRefresh) {
                            emitToast(uiErrorMapper.messageRes(result))
                        }
                    }
                }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private fun openCityPicker(dayId: String) {
        if (_state.value.isPastTrip) return
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

    private fun updateCityQuery(value: String) {
        if (_state.value.isPastTrip) return
        val limitedValue = value.take(TextInputLimits.ITINERARY_CITY_QUERY)
        val query = limitedValue.trim()
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
                itineraryRepository.searchCities(tripId = tripId, query = query, limit = 8)
            }) {
                is ApiResult.Success -> {
                    val suggestions = result.data
                        .distinctBy { it.citySearchDedupeKey() }
                        .map {
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
        if (_state.value.isPastTrip) return
        val picker = _state.value.cityPicker ?: return
        val selectedCityName = city.fullText?.trim().takeUnless { it.isNullOrBlank() } ?: city.name
        val request = UpdateDayRequest(
            city = selectedCityName,
            cityProviderId = city.providerId,
            cityLat = city.lat,
            cityLon = city.lon,
        )
        citySearchJob?.cancel()
        _state.update { it.copy(cityPicker = null) }
        AppLogger.i(
            TAG,
            "selectCity started tripId=$tripId dayId=${picker.dayId} dayDate=${picker.dayDate} city=$selectedCityName"
        )
        viewModelScope.launch {
            var targetDayId = picker.dayId
            var lastFailure: ApiResult.Failure? = null

            when (val initialResult = updateDayOnServer(dayId = targetDayId, request = request)) {
                is ApiResult.Success -> {
                    AppLogger.i(TAG, "selectCity initial update succeeded for dayId=$targetDayId")
                    applySelectedCityLocally(
                        targetDayId = targetDayId,
                        dayDate = picker.dayDate,
                        dayNumber = picker.dayNumber,
                        cityName = selectedCityName,
                    )
                    return@launch
                }

                is ApiResult.Failure -> {
                    AppLogger.w(
                        TAG,
                        "selectCity initial update failed dayId=$targetDayId code=${initialResult.httpCode} apiCode=${initialResult.error?.code.orEmpty()}",
                        initialResult.cause
                    )
                    lastFailure = initialResult
                }
            }

            val latestDays = runCatching {
                itineraryRepository.refreshItinerary(tripId).getOrThrow()
                itineraryRepository.getItinerary(tripId).first()
            }.getOrNull().orEmpty()
            val resolvedDay = latestDays.firstOrNull { it.date == picker.dayDate }
                ?: latestDays.firstOrNull { it.dayNumber == picker.dayNumber }
            if (resolvedDay != null && resolvedDay.id != targetDayId) {
                AppLogger.i(
                    TAG,
                    "selectCity retrying with resolved dayId=${resolvedDay.id} (from $targetDayId)"
                )
                targetDayId = resolvedDay.id
                when (val retryResult = updateDayOnServer(dayId = targetDayId, request = request)) {
                    is ApiResult.Success -> {
                        AppLogger.i(TAG, "selectCity retry update succeeded for dayId=$targetDayId")
                        applySelectedCityLocally(
                            targetDayId = targetDayId,
                            dayDate = picker.dayDate,
                            dayNumber = picker.dayNumber,
                            cityName = selectedCityName,
                        )
                        return@launch
                    }

                    is ApiResult.Failure -> {
                        AppLogger.w(
                            TAG,
                            "selectCity retry update failed dayId=$targetDayId code=${retryResult.httpCode} apiCode=${retryResult.error?.code.orEmpty()}",
                            retryResult.cause
                        )
                        lastFailure = retryResult
                    }
                }
            }

            val appliedOnServer = runCatching {
                itineraryRepository.refreshItinerary(tripId).getOrThrow()
                itineraryRepository.getItinerary(tripId).first().any { day ->
                    (day.id == targetDayId ||
                        day.date == picker.dayDate ||
                        day.dayNumber == picker.dayNumber) &&
                        day.city?.trim()?.equals(selectedCityName.trim(), ignoreCase = true) == true
                }
            }.getOrDefault(false)

            if (appliedOnServer) {
                AppLogger.i(TAG, "selectCity confirmed on server for dayId=$targetDayId")
                applySelectedCityLocally(
                    targetDayId = targetDayId,
                    dayDate = picker.dayDate,
                    dayNumber = picker.dayNumber,
                    cityName = selectedCityName,
                )
            } else {
                val failure = checkNotNull(lastFailure) {
                    "selectCity failed without failure context for dayId=$targetDayId"
                }
                AppLogger.w(
                    TAG,
                    "selectCity failed after retries for dayId=$targetDayId code=${failure.httpCode} apiCode=${failure.error?.code.orEmpty()}",
                    failure.cause
                )
                emitToast(uiErrorMapper.messageRes(failure))
            }
        }
    }

    private fun selectCityForFollowingDays(city: CitySuggestionUi) {
        if (_state.value.isPastTrip) return
        val picker = _state.value.cityPicker ?: return
        val selectedCityName = city.fullText?.trim().takeUnless { it.isNullOrBlank() } ?: city.name
        val request = UpdateDayRequest(
            city = selectedCityName,
            cityProviderId = city.providerId,
            cityLat = city.lat,
            cityLon = city.lon,
        )
        citySearchJob?.cancel()
        _state.update { it.copy(cityPicker = null) }
        AppLogger.i(
            TAG,
            "selectCityForFollowingDays started tripId=$tripId fromDay=${picker.dayNumber} city=$selectedCityName"
        )
        viewModelScope.launch {
            val latestDays = runCatching {
                itineraryRepository.refreshItinerary(tripId).getOrThrow()
                itineraryRepository.getItinerary(tripId).first()
                    .filter { !it.isOutOfRange && it.dayNumber >= picker.dayNumber }
                    .sortedBy { it.dayNumber }
            }.getOrNull().orEmpty()

            if (latestDays.isEmpty()) {
                emitToast(R.string.common_error_message)
                return@launch
            }

            var successCount = 0
            var firstFailure: ApiResult.Failure? = null
            latestDays.forEach { day ->
                when (val result = updateDayOnServer(dayId = day.id, request = request)) {
                    is ApiResult.Success -> successCount += 1
                    is ApiResult.Failure -> {
                        if (firstFailure == null) {
                            firstFailure = result
                        }
                        AppLogger.w(
                            TAG,
                            "selectCityForFollowingDays failed dayId=${day.id} code=${result.httpCode} apiCode=${result.error?.code.orEmpty()}",
                            result.cause
                        )
                    }
                }
            }

            if (successCount > 0) {
                applySelectedCityForFollowingDaysLocally(
                    fromDayNumber = picker.dayNumber,
                    cityName = selectedCityName,
                )
                runCatching { itineraryRepository.refreshItinerary(tripId).getOrThrow() }
            } else if (firstFailure != null) {
                emitToast(uiErrorMapper.messageRes(checkNotNull(firstFailure)))
            } else {
                emitToast(R.string.common_error_message)
            }
        }
    }

    private suspend fun updateDayOnServer(
        dayId: String,
        request: UpdateDayRequest,
    ): ApiResult<Unit> {
        return apiCaller.call {
            itineraryRepository.updateDay(dayId = dayId, request = request)
        }
    }

    private suspend fun applySelectedCityLocally(
        targetDayId: String,
        dayDate: String,
        dayNumber: Int,
        cityName: String,
    ) {
        _state.update { st ->
            val days = st.days.map { d ->
                if (d.id == targetDayId || d.dateIso == dayDate || d.dayNumber == dayNumber) {
                    d.copy(city = cityName)
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
        runCatching { itineraryRepository.refreshItinerary(tripId).getOrThrow() }
    }

    private fun applySelectedCityForFollowingDaysLocally(
        fromDayNumber: Int,
        cityName: String,
    ) {
        _state.update { st ->
            val days = st.days.map { d ->
                if (d.dayNumber >= fromDayNumber) {
                    d.copy(city = cityName)
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
    }

    private fun completeRequiredCitySelection() {
        val current = _state.value
        if (!current.isCitySelectionRequired) return
        if (current.pendingCitySelectionCount > 0) {
            _state.update { it.copy(inlineErrorRes = R.string.itinerary_city_setup_required_toast) }
            return
        }
        viewModelScope.launch {
            clearPendingCreationTrip()
            appNavigator.navigate(Destination.TripDetails(tripId)) {
                popUpTo(Destination.Trips.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripItineraryEffect.ShowToastRes(resId)) }
    }

    private suspend fun clearPendingCreationTrip() {
        runCatching { pendingTripCreationStore.clearPendingTripId(tripId) }
            .onFailure { AppLogger.w(TAG, "Failed to clear pending tripId=$tripId", it) }
    }

    private companion object {
        private const val TAG = "TripItineraryVM"
    }
}

private fun collectTripCities(days: List<ItineraryDayDto>): List<CitySuggestionUi> {
    val byName = linkedMapOf<String, CitySuggestionUi>()
    for (day in days) {
        val cityName = day.city?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val cityLat = day.cityLat ?: continue
        val cityLon = day.cityLon ?: continue
        val key = cityName.lowercase(appUiLocale())
        val label = day.cityDisplayLabel()
        val candidate = CitySuggestionUi(
            name = label,
            providerId = day.cityProviderId,
            lat = cityLat,
            lon = cityLon,
            fullText = label,
        )
        val current = byName[key]
        byName[key] = if (current == null || (current.providerId == null && candidate.providerId != null)) {
            candidate
        } else {
            current
        }
    }
    return byName.values.toList().distinctBy { it.tripCityDedupeKey() }
}

private fun CitySuggestionDto.citySearchDedupeKey(): String {
    val trimmedProvider = providerId?.trim()?.takeIf { it.isNotEmpty() }
    return trimmedProvider ?: "$lat:$lon:${name.trim()}"
}

private fun CitySuggestionUi.tripCityDedupeKey(): String {
    val trimmedProvider = providerId?.trim()?.takeIf { it.isNotEmpty() }
    return trimmedProvider ?: "$lat:$lon:${name.trim().lowercase(appUiLocale())}"
}

private fun ItineraryDayDto.toUi(currencySymbol: String): ItineraryDayUi {
    val date = LocalDate.parse(date)
    val dateText = DateTimeFormatter.ofPattern("EEE, MMM d", appUiLocale()).format(date)
    val activities = activities.sortedBy { it.orderIndex }.map { it.toUi(currencySymbol) }
    val label = cityDisplayLabel()
    return ItineraryDayUi(
        id = id,
        dayNumber = dayNumber,
        dateIso = this.date,
        dateText = dateText,
        city = label.takeIf { it.isNotEmpty() },
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
    val locale = appUiLocale()
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
        String.format(appUiLocale(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}
