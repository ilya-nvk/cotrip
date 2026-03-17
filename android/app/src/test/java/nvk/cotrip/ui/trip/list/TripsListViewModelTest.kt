package nvk.cotrip.ui.trip.list

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.sync.SyncPullRepository
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appNavigator = mockk<AppNavigator>(relaxed = true)
    private val tripRepository = mockk<TripRepository>()
    private val syncPullRepository = mockk<SyncPullRepository>()

    @Test
    fun init_buildsBucketsAndMapsMembersIntoCards() = runTest {
        val today = LocalDate.now()
        val activeTrip = trip(
            id = "active",
            startDate = today.minusDays(1).toString(),
            endDate = today.plusDays(1).toString(),
        )
        val pastTrip = trip(
            id = "past",
            startDate = today.minusDays(10).toString(),
            endDate = today.minusDays(5).toString(),
        )
        val tripsFlow = MutableStateFlow(listOf(activeTrip, pastTrip))
        every { tripRepository.trips } returns tripsFlow
        every { tripRepository.tripMembers(any()) } answers {
            flowOf(
                listOf(
                    MemberDto(
                        userId = "u1",
                        name = "User One",
                        initials = "UO",
                        role = "member",
                        status = "accepted",
                        photoUrl = null,
                    )
                )
            )
        }
        coEvery { tripRepository.refreshTrips() } returns Result.success(Unit)
        coEvery { syncPullRepository.pull() } returns Result.success(Unit)

        val context: Context = ApplicationProvider.getApplicationContext()
        val viewModel = TripsListViewModel(
            appContext = context,
            appNavigator = appNavigator,
            tripRepository = tripRepository,
            syncPullRepository = syncPullRepository,
        )

        advanceUntilIdle()

        val state = viewModel.state.value as TripsListUiState.Content
        assertEquals(1, state.activeTrips.size)
        assertEquals(1, state.pastTrips.size)
        assertEquals("active", state.activeTrips.first().id)
        assertEquals(1, state.activeTrips.first().avatars.size)
    }

    @Test
    fun clickEvents_navigateToExpectedDestinations() = runTest {
        every { tripRepository.trips } returns MutableStateFlow(emptyList())
        every { tripRepository.tripMembers(any()) } returns flowOf(emptyList())
        coEvery { tripRepository.refreshTrips() } returns Result.success(Unit)
        coEvery { syncPullRepository.pull() } returns Result.success(Unit)

        val context: Context = ApplicationProvider.getApplicationContext()
        val viewModel = TripsListViewModel(
            appContext = context,
            appNavigator = appNavigator,
            tripRepository = tripRepository,
            syncPullRepository = syncPullRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(TripsListEvent.OnSettingsClick)
        viewModel.onEvent(TripsListEvent.OnCreateTripClick)
        viewModel.onEvent(TripsListEvent.OnJoinTripClick)
        viewModel.onEvent(TripsListEvent.OnTripClick("trip-42"))

        verify { appNavigator.navigate(Destination.Settings, null) }
        verify { appNavigator.navigate(Destination.CreateTrip, null) }
        verify { appNavigator.navigate(Destination.JoinTrip(), null) }
        verify { appNavigator.navigate(Destination.TripDetails("trip-42"), null) }
    }

    private fun trip(
        id: String,
        startDate: String,
        endDate: String,
    ): TripDto {
        return TripDto(
            id = id,
            ownerId = "owner",
            title = "Trip $id",
            startDate = startDate,
            endDate = endDate,
            currencyCode = "EUR",
            status = "active",
            updatedAt = "2026-03-16T10:00:00Z",
            description = null,
            locationLine = null,
            coverUrl = null,
        )
    }
}
