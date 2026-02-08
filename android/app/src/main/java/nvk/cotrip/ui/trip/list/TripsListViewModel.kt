package nvk.cotrip.ui.trip.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TripsListViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val api: CoTripApi,
) : ViewModel() {

    private val _state = MutableStateFlow<TripsListUiState>(TripsListUiState.Loading)
    val state: StateFlow<TripsListUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripsListEffect>(replay = 0, extraBufferCapacity = 1)
    val effects: SharedFlow<TripsListEffect> = _effects.asSharedFlow()

    init {
        loadTrips()
    }

    fun onEvent(event: TripsListEvent) {
        when (event) {
            TripsListEvent.OnSettingsClick -> appNavigator.navigate(Destination.Settings)
            TripsListEvent.OnCreateTripClick -> appNavigator.navigate(Destination.CreateTrip)
            is TripsListEvent.OnTripClick -> appNavigator.navigate(Destination.TripDetails(event.id))
            TripsListEvent.OnTogglePast -> _state.update {
                (it as? TripsListUiState.Content)?.copy(showPastTrips = !it.showPastTrips) ?: it
            }
        }
    }

    private fun loadTrips() {
        viewModelScope.launch {
            runCatching {
                val active = api.listTrips(status = "active").items
                val upcoming = api.listTrips(status = "upcoming").items
                val past = api.listTrips(status = "past").items
                TripBuckets(active, upcoming, past)
            }.onSuccess { buckets ->
                _state.update {
                    TripsListUiState.Content(
                        activeTrips = buckets.active.map { it.toCard() },
                        upcomingTrips = buckets.upcoming.map { it.toCard() },
                        pastTrips = buckets.past.map { it.toCard() },
                    )
                }
            }.onFailure {
                _effects.tryEmit(TripsListEffect.ShowToast("Failed to load trips."))
                _state.update {
                    TripsListUiState.Content()
                }
            }
        }
    }

    private data class TripBuckets(
        val active: List<TripDto>,
        val upcoming: List<TripDto>,
        val past: List<TripDto>,
    )
}

private fun TripDto.toCard(): TripCardUi {
    val start = LocalDate.parse(startDate)
    val end = LocalDate.parse(endDate)
    val dateRange = formatRange(start, end)
    val isInProgress = status == "active" && !LocalDate.now().isBefore(start) && !LocalDate.now().isAfter(end)
    return TripCardUi(
        id = id,
        title = title,
        dateRange = dateRange,
        locationLine = locationLine.orEmpty(),
        peopleCountText = "Members",
        initials = emptyList(),
        isInProgress = isInProgress,
        coverUrl = coverUrl,
    )
}

private fun formatRange(start: LocalDate, end: LocalDate): String {
    val locale = Locale.getDefault()
    val sameYear = start.year == end.year
    val startFormat = if (sameYear) "MMM d" else "MMM d, yyyy"
    val endFormat = "MMM d, yyyy"
    val startText = start.format(DateTimeFormatter.ofPattern(startFormat, locale))
    val endText = end.format(DateTimeFormatter.ofPattern(endFormat, locale))
    return "$startText – $endText"
}
