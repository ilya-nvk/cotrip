package nvk.cotrip.ui.trip.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.sync.SyncPullRepository
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.components.AvatarStackItem
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TripsListViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
    private val membersByTrip = MutableStateFlow<Map<String, List<AvatarStackItem>>>(emptyMap())

    init {
        observeTrips()
        observeTripMembers()
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
            TripsListEvent.OnTogglePast -> showPastTrips.update { !it }
        }
    }

    private fun observeTrips() {
        viewModelScope.launch {
            combine(
                tripRepository.trips,
                showPastTrips,
                isRefreshing,
                membersByTrip
            ) { trips, showPast, refreshing, members ->
                val buckets = buildBuckets(trips)
                TripsListUiState.Content(
                    activeTrips = buckets.active.map {
                        it.toCard(
                            members[it.id].orEmpty(),
                            appContext
                        )
                    },
                    upcomingTrips = buckets.upcoming.map {
                        it.toCard(
                            members[it.id].orEmpty(),
                            appContext
                        )
                    },
                    pastTrips = buckets.past.map {
                        it.toCard(
                            members[it.id].orEmpty(),
                            appContext
                        )
                    },
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
                _effects.tryEmit(
                    TripsListEffect.ShowToast(appContext.getString(R.string.trips_list_load_failed))
                )
            } else if (syncResult.isFailure) {
                _effects.tryEmit(
                    TripsListEffect.ShowToast(appContext.getString(R.string.trips_list_sync_failed))
                )
            }
            isRefreshing.value = false
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTripMembers() {
        viewModelScope.launch {
            tripRepository.trips
                .flatMapLatest { trips ->
                    if (trips.isEmpty()) {
                        return@flatMapLatest flowOf(emptyMap())
                    }

                    val memberFlows = trips.map { trip ->
                        tripRepository.tripMembers(trip.id).map { members ->
                            trip.id to members.map { member ->
                                AvatarStackItem(
                                    initials = member.initials,
                                    photoUrl = member.photoUrl
                                )
                            }
                        }
                    }

                    combine(memberFlows) { perTrip ->
                        perTrip.toMap()
                    }
                }
                .collect { resolved ->
                    membersByTrip.value = resolved
                }
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

private fun TripDto.toCard(avatars: List<AvatarStackItem>, context: Context): TripCardUi {
    val start = LocalDate.parse(startDate)
    val end = LocalDate.parse(endDate)
    val dateRange = formatRange(start, end)
    val isInProgress = status == "active" && !LocalDate.now().isBefore(start) && !LocalDate.now().isAfter(end)
    return TripCardUi(
        id = id,
        title = title,
        dateRange = dateRange,
        locationLine = locationLine.orEmpty(),
        peopleCountText = context.resources.getQuantityString(
            R.plurals.people_count,
            avatars.size,
            avatars.size
        ),
        avatars = avatars,
        isInProgress = isInProgress,
        coverUrl = coverUrl,
    )
}

private fun formatRange(start: LocalDate, end: LocalDate): String {
    val locale = appUiLocale()
    val sameYear = start.year == end.year
    val startFormat = if (sameYear) "MMM d" else "MMM d, yyyy"
    val endFormat = "MMM d, yyyy"
    val startText = start.format(DateTimeFormatter.ofPattern(startFormat, locale))
    val endText = end.format(DateTimeFormatter.ofPattern(endFormat, locale))
    return "$startText – $endText"
}
