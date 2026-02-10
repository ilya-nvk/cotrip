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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.sync.SyncPullRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TripsListViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val syncPullRepository: SyncPullRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<TripsListUiState>(TripsListUiState.Loading)
    val state: StateFlow<TripsListUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripsListEffect>(replay = 0, extraBufferCapacity = 1)
    val effects: SharedFlow<TripsListEffect> = _effects.asSharedFlow()

    private val showPastTrips = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)

    init {
        observeTrips()
        refreshTrips(isUserRefresh = false)
    }

    fun onEvent(event: TripsListEvent) {
        when (event) {
            TripsListEvent.OnSettingsClick -> appNavigator.navigate(Destination.Settings)
            TripsListEvent.OnCreateTripClick -> appNavigator.navigate(Destination.CreateTrip)
            TripsListEvent.OnJoinTripClick -> appNavigator.navigate(Destination.JoinTrip())
            TripsListEvent.OnAutoRefresh -> refreshTrips(isUserRefresh = false)
            TripsListEvent.OnUserRefresh -> refreshTrips(isUserRefresh = true)
            is TripsListEvent.OnTripClick -> appNavigator.navigate(Destination.TripDetails(event.id))
            TripsListEvent.OnTogglePast -> _state.update {
                val current = it as? TripsListUiState.Content ?: return@update it
                showPastTrips.value = !current.showPastTrips
                current.copy(showPastTrips = showPastTrips.value)
            }
        }
    }

    private fun observeTrips() {
        viewModelScope.launch {
            combine(
                tripRepository.trips,
                showPastTrips,
                isRefreshing
            ) { trips, showPast, refreshing ->
                val buckets = buildBuckets(trips)
                TripsListUiState.Content(
                    activeTrips = buckets.active.map { it.toCard() },
                    upcomingTrips = buckets.upcoming.map { it.toCard() },
                    pastTrips = buckets.past.map { it.toCard() },
                    showPastTrips = showPast,
                    isRefreshing = refreshing,
                )
            }.collect { content ->
                _state.value = content
            }
        }
    }

    private fun refreshTrips(isUserRefresh: Boolean) {
        viewModelScope.launch {
            val currentContent = _state.value as? TripsListUiState.Content
            if (isUserRefresh) {
                isRefreshing.value = true
            } else if (currentContent == null) {
                _state.value = TripsListUiState.Loading
            }

            val result = tripRepository.refreshTrips()
            val syncResult = syncPullRepository.pull()
            if (result.isFailure) {
                _effects.tryEmit(TripsListEffect.ShowToast("Failed to load trips."))
            } else if (syncResult.isFailure) {
                _effects.tryEmit(TripsListEffect.ShowToast("Failed to sync updates."))
            }
            isRefreshing.value = false
        }
    }

    private data class TripBuckets(
        val active: List<TripDto>,
        val upcoming: List<TripDto>,
        val past: List<TripDto>,
    )

    private fun buildBuckets(trips: List<TripDto>): TripBuckets {
        val today = LocalDate.now()
        val nonArchived = trips.filter { it.status != "archived" }
        val active = mutableListOf<TripDto>()
        val upcoming = mutableListOf<TripDto>()
        val past = mutableListOf<TripDto>()

        nonArchived.forEach { trip ->
            val start = runCatching { LocalDate.parse(trip.startDate) }.getOrNull()
            val end = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()
            if (start == null || end == null) return@forEach

            when {
                end.isBefore(today) -> past += trip
                start.isAfter(today) -> upcoming += trip
                else -> active += trip
            }
        }

        return TripBuckets(
            active = active,
            upcoming = upcoming,
            past = past,
        )
    }
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
