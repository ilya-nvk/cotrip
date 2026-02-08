package nvk.cotrip.ui.trip.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TripsListViewModel @Inject constructor(
    private val appNavigator: AppNavigator
) : ViewModel() {

    private val _state = MutableStateFlow<TripsListUiState>(TripsListUiState.Loading)
    val state: StateFlow<TripsListUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripsListEffect>(replay = 0, extraBufferCapacity = 1)
    val effects: SharedFlow<TripsListEffect> = _effects.asSharedFlow()

    init {
        // DEMO загрузка
        viewModelScope.launch {
            delay(600)
            _state.update {
                TripsListUiState.Content(
                    activeTrips = listOf(
                        TripCardUi(
                            id = UUID.randomUUID().toString(),
                            title = "Weekend Getaway",
                            dateRange = "Jan 30 – Feb 2, 2026",
                            locationLine = "Lake Tahoe",
                            initials = listOf("JD", "SM"),
                            peopleCountText = "2 people",
                            isInProgress = true
                        )
                    ),
                    upcomingTrips = listOf(
                        TripCardUi(
                            id = UUID.randomUUID().toString(),
                            title = "Summer Europe Trip",
                            dateRange = "Jul 15 – Jul 29, 2026",
                            locationLine = "Paris, Rome, Barcelona",
                            initials = listOf("JD", "SM", "AK", "MR"),
                            peopleCountText = "4 people",
                        )
                    ),
                    pastTrips = listOf(
                        TripCardUi(
                            id = UUID.randomUUID().toString(),
                            title = "Tokyo Autumn",
                            dateRange = "Oct 10 – Oct 22, 2025",
                            locationLine = "Tokyo, Kyoto",
                            initials = listOf("JD", "SM"),
                            peopleCountText = "2 people",
                        ),
                        TripCardUi(
                            id = UUID.randomUUID().toString(),
                            title = "Sochi Weekend",
                            dateRange = "Jul 4 – Jul 7, 2025",
                            locationLine = "Esto-Sadok",
                            initials = listOf("JD"),
                            peopleCountText = "1 person",
                        )
                    )
                )
            }
        }
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
}