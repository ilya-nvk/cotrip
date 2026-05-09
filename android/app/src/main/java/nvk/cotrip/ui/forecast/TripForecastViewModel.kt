package nvk.cotrip.ui.forecast

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.data.network.dto.cityDisplayLabel
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.WeatherRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class TripForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val weatherRepository: WeatherRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripForecast.ARG_TRIP_ID])

    private val _state = MutableStateFlow<TripForecastState>(TripForecastState.Loading)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripForecastEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            bootstrapFromCache()
            refreshForecast(isUserRefresh = false, forceRefresh = true, showErrorToast = false)
        }
    }

    fun onEvent(event: TripForecastEvent) {
        when (event) {
            TripForecastEvent.OnBackClick -> appNavigator.popBackStack()
            TripForecastEvent.OnAutoRefresh -> refreshForecast(
                isUserRefresh = false,
                forceRefresh = true,
                showErrorToast = false,
            )
            TripForecastEvent.OnUserRefresh -> refreshForecast(
                isUserRefresh = true,
                showErrorToast = true,
            )
            TripForecastEvent.OnCityClick -> {
                val current = _state.value as? TripForecastState.Content ?: return
                if (current.cityOptions.size > 1) {
                    _state.value = current.copy(isCityPickerVisible = true)
                }
            }

            TripForecastEvent.OnDismissCityPicker -> {
                val current = _state.value as? TripForecastState.Content ?: return
                _state.value = current.copy(isCityPickerVisible = false)
            }
            is TripForecastEvent.OnCitySelected -> {
                val current = _state.value as? TripForecastState.Content ?: return
                val label = current.cityOptions.firstOrNull { it.key == event.city }?.label
                    ?: event.city
                _state.value = current.copy(
                    city = label,
                    weatherCityKey = event.city,
                    isCityPickerVisible = false,
                )
                refreshForecast(
                    isUserRefresh = false,
                    forceRefresh = true,
                    showErrorToast = true,
                )
            }
        }
    }

    private suspend fun bootstrapFromCache() {
        val trip = tripRepository.getTrip(tripId).first()
        val itinerary = itineraryRepository.getItinerary(tripId).first()
        val cityOptions = collectCityOptions(itinerary)
        val selectedCity = selectCity(itinerary, preferredWeatherCityKey = null)
        if (selectedCity == null) {
            _state.value = TripForecastState.Content(
                city = "",
                weatherCityKey = "",
                cityOptions = cityOptions,
                isCityPickerVisible = false,
                days = emptyList(),
                source = TripForecastUiMapper.source(appContext, WeatherForecastResponseDto()),
                lastUpdated = "",
                coverageMessage = TripForecastUiMapper.coverageMessage(
                    context = appContext,
                    hasSelectedCity = false,
                    response = WeatherForecastResponseDto(),
                ),
                isRefreshing = false,
            )
            return
        }

        val cached = weatherRepository.getCachedWeather(
            tripId = tripId,
            city = selectedCity.key,
            start = trip.startDate,
            end = trip.endDate,
        ) ?: WeatherForecastResponseDto(items = emptyList())
        val cityLabel = cached.displayCity?.takeIf { it.isNotBlank() } ?: selectedCity.displayLabel
        applyLoaded(
            WeatherLoadResult(
                weatherCityKey = selectedCity.key,
                cityLabel = cityLabel,
                cityOptions = cityOptions,
                response = cached,
                hasSelectedCity = true,
            ),
            isRefreshing = false,
        )
    }

    private fun refreshForecast(
        isUserRefresh: Boolean,
        forceRefresh: Boolean = false,
        showErrorToast: Boolean = false,
    ) {
        viewModelScope.launch {
            val current = _state.value as? TripForecastState.Content
            if (isUserRefresh) {
                if (current != null) {
                    _state.value = current.copy(isRefreshing = true)
                }
            }

            val result = apiCaller.call {
                val trip = tripRepository.getTrip(tripId).first()
                runCatching { itineraryRepository.refreshItinerary(tripId) }
                val itinerary = itineraryRepository.getItinerary(tripId).first()
                val cityOptions = collectCityOptions(itinerary)
                val preferredKey = current?.weatherCityKey?.trim()?.takeIf { it.isNotEmpty() }
                val selectedCity = selectCity(itinerary, preferredKey)
                if (selectedCity == null) {
                    WeatherLoadResult(
                        weatherCityKey = "",
                        cityLabel = "",
                        cityOptions = cityOptions,
                        response = WeatherForecastResponseDto(items = emptyList()),
                        hasSelectedCity = false,
                    )
                } else {
                    val shouldRefresh =
                        forceRefresh ||
                            isUserRefresh ||
                            current == null ||
                            current.days.isEmpty() ||
                            current.weatherCityKey.isBlank() ||
                            !current.weatherCityKey.equals(selectedCity.key, ignoreCase = true)
                    if (shouldRefresh) {
                        val refreshResult = weatherRepository.refreshWeather(
                            tripId = tripId,
                            city = selectedCity.key,
                            start = trip.startDate,
                            end = trip.endDate,
                        )
                        if (refreshResult.isFailure && isUserRefresh) {
                            throw refreshResult.exceptionOrNull()
                                ?: IOException("Weather refresh failed")
                        }
                    }
                    val response = weatherRepository.getCachedWeather(
                        tripId = tripId,
                        city = selectedCity.key,
                        start = trip.startDate,
                        end = trip.endDate,
                    ) ?: weatherRepository.getWeather(
                        tripId = tripId,
                        city = selectedCity.key,
                        start = trip.startDate,
                        end = trip.endDate,
                    ).first()
                    val cityLabel = response.displayCity?.takeIf { it.isNotBlank() } ?: selectedCity.displayLabel
                    WeatherLoadResult(
                        weatherCityKey = selectedCity.key,
                        cityLabel = cityLabel,
                        cityOptions = cityOptions,
                        response = response,
                        hasSelectedCity = true,
                    )
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    applyLoaded(result.data, isRefreshing = false)
                }

                is ApiResult.Failure -> {
                    val latest = _state.value as? TripForecastState.Content
                    if (latest != null) {
                        _state.value = latest.copy(isRefreshing = false)
                    }
                    if (showErrorToast) {
                        emitToast(uiErrorMapper.messageRes(result))
                    }
                }
            }
        }
    }

    private fun applyLoaded(loaded: WeatherLoadResult, isRefreshing: Boolean) {
        val mappedDays = TripForecastUiMapper.mapDays(appContext, loaded.response)
        val source = TripForecastUiMapper.source(appContext, loaded.response)
        val lastUpdated = TripForecastUiMapper.lastUpdated(loaded.response)

        _state.value = TripForecastState.Content(
            city = loaded.cityLabel,
            weatherCityKey = loaded.weatherCityKey,
            cityOptions = loaded.cityOptions,
            isCityPickerVisible = false,
            days = mappedDays,
            source = source,
            lastUpdated = lastUpdated,
            coverageMessage = TripForecastUiMapper.coverageMessage(
                context = appContext,
                hasSelectedCity = loaded.hasSelectedCity,
                response = loaded.response,
            ),
            isRefreshing = isRefreshing,
        )
    }

    private fun selectCity(days: List<ItineraryDayDto>): SelectedCity? {
        return days
            .sortedBy { it.dayNumber }
            .firstOrNull { day ->
                !day.city.isNullOrBlank() && day.cityLat != null && day.cityLon != null
            }
            ?.let { day ->
                SelectedCity(
                    key = day.city.orEmpty(),
                    displayLabel = day.cityDisplayLabel(),
                )
            }
    }

    private fun selectCity(
        days: List<ItineraryDayDto>,
        preferredWeatherCityKey: String?,
    ): SelectedCity? {
        val normalizedPreferred = preferredWeatherCityKey?.trim()?.takeIf { it.isNotEmpty() }
        val sorted = days.sortedBy { it.dayNumber }
        if (normalizedPreferred != null) {
            val preferredDay = sorted.firstOrNull { day ->
                !day.city.isNullOrBlank() &&
                    day.cityLat != null &&
                    day.cityLon != null &&
                    day.city.equals(normalizedPreferred, ignoreCase = true)
            }
            if (preferredDay != null) {
                return SelectedCity(
                    key = preferredDay.city.orEmpty(),
                    displayLabel = preferredDay.cityDisplayLabel(),
                )
            }
        }
        return selectCity(sorted)
    }

    private fun collectCityOptions(days: List<ItineraryDayDto>): List<WeatherCityOption> {
        val seenDisplayKeys = linkedSetOf<String>()
        val result = mutableListOf<WeatherCityOption>()
        days.sortedBy { it.dayNumber }.forEach { day ->
            val key = day.city?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            if (day.cityLat == null || day.cityLon == null) return@forEach
            val label = day.cityDisplayLabel()
            val displayKey = label.trim().lowercase(appUiLocale())
            if (displayKey in seenDisplayKeys) return@forEach
            seenDisplayKeys.add(displayKey)
            result += WeatherCityOption(key = key, label = label)
        }
        return result
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripForecastEffect.ShowToastRes(resId)) }
    }

    private data class SelectedCity(
        val key: String,
        val displayLabel: String,
    )

    private data class WeatherLoadResult(
        val weatherCityKey: String,
        val cityLabel: String,
        val cityOptions: List<WeatherCityOption>,
        val response: WeatherForecastResponseDto,
        val hasSelectedCity: Boolean,
    )
}
