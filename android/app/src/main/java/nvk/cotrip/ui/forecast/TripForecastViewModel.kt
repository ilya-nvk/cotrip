package nvk.cotrip.ui.forecast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.WeatherRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class TripForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val weatherRepository: WeatherRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripForecast.ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        TripForecastState(
            city = "",
            cityOptions = emptyList(),
            isCityPickerVisible = false,
            days = emptyList(),
            source = "OpenWeather",
            lastUpdated = "",
            coverageMessage = null,
            isLoading = true,
            isRefreshing = false,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripForecastEffect>()
    val effects = _effects.asSharedFlow()

    init {
        refreshForecast(isUserRefresh = false)
    }

    fun onEvent(event: TripForecastEvent) {
        when (event) {
            TripForecastEvent.OnBackClick -> appNavigator.popBackStack()
            TripForecastEvent.OnAutoRefresh -> refreshForecast(isUserRefresh = false)
            TripForecastEvent.OnUserRefresh -> refreshForecast(isUserRefresh = true)
            TripForecastEvent.OnCityClick -> {
                if (_state.value.cityOptions.isEmpty()) {
                    emitToast(R.string.weather_forecast_city_missing)
                } else {
                    _state.value = _state.value.copy(isCityPickerVisible = true)
                }
            }
            TripForecastEvent.OnDismissCityPicker ->
                _state.value = _state.value.copy(isCityPickerVisible = false)
            is TripForecastEvent.OnCitySelected -> {
                _state.value = _state.value.copy(
                    city = event.city,
                    isCityPickerVisible = false,
                )
                refreshForecast(isUserRefresh = false)
            }
        }
    }

    private fun refreshForecast(isUserRefresh: Boolean) {
        viewModelScope.launch {
            if (isUserRefresh) {
                _state.value = _state.value.copy(isRefreshing = true)
            } else if (_state.value.days.isEmpty()) {
                _state.value = _state.value.copy(isLoading = true)
            }

            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val trip = tripRepository.getTrip(tripId).first()
                    itineraryRepository.refreshItinerary(tripId).getOrThrow()
                    val itinerary = itineraryRepository.getItinerary(tripId).first()
                    val cityOptions = collectCityOptions(itinerary)
                    val selectedCity = selectCity(itinerary, _state.value.city)
                    if (selectedCity == null) {
                        WeatherLoadResult(
                            city = "",
                            cityOptions = cityOptions,
                            response = WeatherForecastResponseDto(items = emptyList()),
                            hasSelectedCity = false,
                        )
                    } else {
                        val shouldRefresh =
                            isUserRefresh ||
                                _state.value.days.isEmpty() ||
                                !_state.value.city.equals(selectedCity.city, ignoreCase = true)
                        if (shouldRefresh) {
                            weatherRepository.refreshWeather(
                                tripId = tripId,
                                city = selectedCity.city,
                                start = trip.startDate,
                                end = trip.endDate,
                            ).getOrThrow()
                        }
                        val response = weatherRepository.getWeather(
                            tripId = tripId,
                            city = selectedCity.city,
                            start = trip.startDate,
                            end = trip.endDate,
                        ).first()
                        WeatherLoadResult(
                            city = selectedCity.city,
                            cityOptions = cityOptions,
                            response = response,
                            hasSelectedCity = true,
                        )
                    }
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    val loaded = result.data
                    val mappedDays = TripForecastUiMapper.mapDays(loaded.response)
                    val source = TripForecastUiMapper.source(loaded.response)
                    val lastUpdated = TripForecastUiMapper.lastUpdated(loaded.response)

                    _state.value = _state.value.copy(
                        city = loaded.city,
                        cityOptions = loaded.cityOptions,
                        isCityPickerVisible = false,
                        days = mappedDays,
                        source = source,
                        lastUpdated = lastUpdated,
                        coverageMessage = TripForecastUiMapper.coverageMessage(
                            hasSelectedCity = loaded.hasSelectedCity,
                            response = loaded.response,
                        ),
                        isLoading = false,
                        isRefreshing = false,
                    )
                }

                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
                    emitToast(uiErrorMapper.messageRes(result))
                }
            }
        }
    }

    private fun selectCity(days: List<ItineraryDayDto>): SelectedCity? {
        return days
            .sortedBy { it.dayNumber }
            .firstOrNull { day ->
                !day.city.isNullOrBlank() && day.cityLat != null && day.cityLon != null
            }
            ?.let { day -> SelectedCity(city = day.city.orEmpty()) }
    }

    private fun selectCity(
        days: List<ItineraryDayDto>,
        preferredCity: String?,
    ): SelectedCity? {
        val normalizedPreferred = preferredCity?.trim()?.takeIf { it.isNotEmpty() }
        val sorted = days.sortedBy { it.dayNumber }
        if (normalizedPreferred != null) {
            val preferredDay = sorted.firstOrNull { day ->
                !day.city.isNullOrBlank() &&
                    day.cityLat != null &&
                    day.cityLon != null &&
                    day.city.equals(normalizedPreferred, ignoreCase = true)
            }
            if (preferredDay != null) {
                return SelectedCity(city = preferredDay.city.orEmpty())
            }
        }
        return selectCity(sorted)
    }

    private fun collectCityOptions(days: List<ItineraryDayDto>): List<String> {
        val seen = linkedSetOf<String>()
        days.sortedBy { it.dayNumber }.forEach { day ->
            val city = day.city?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            if (day.cityLat == null || day.cityLon == null) return@forEach
            seen.add(city)
        }
        return seen.toList()
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripForecastEffect.ShowToastRes(resId)) }
    }

    private data class SelectedCity(
        val city: String,
    )

    private data class WeatherLoadResult(
        val city: String,
        val cityOptions: List<String>,
        val response: WeatherForecastResponseDto,
        val hasSelectedCity: Boolean,
    )
}
