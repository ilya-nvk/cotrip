package nvk.cotrip.ui.trip.members

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
import nvk.cotrip.ui.trip.details.TripDetailsFakeTripRepository
import nvk.cotrip.ui.trip.details.TripDetailsFakeUserRepository
import nvk.cotrip.ui.trip.details.tripDetailsMemberDto
import nvk.cotrip.ui.trip.details.tripDetailsTripDto
import nvk.cotrip.ui.trip.details.tripDetailsUserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class TripMembersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_tripWithMembers_when_init_then_showsContentWithMembers() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val trip = tripDetailsTripDto(
            id = "trip-1",
            ownerId = "owner-1",
            start = today.plusDays(1),
            end = today.plusDays(3),
        )
        val members = listOf(
            tripDetailsMemberDto(id = "owner-1", initials = "OW"),
            tripDetailsMemberDto(id = "guest-1", initials = "GT"),
        )
        val tripRepository = TripDetailsFakeTripRepository(trip = trip, members = members).apply {
            refreshTripsResult = Result.success(Unit)
        }
        val viewModel = createViewModel(
            navigator = TripDetailsFakeNavigator(),
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            tripId = "trip-1",
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val content = viewModel.state.value as? TripMembersState.Content
        assertNotNull(content)
        assertEquals("trip-1", content!!.tripId)
        assertEquals(2, content.members.size)
        assertEquals("owner-1", content.meId)
        assertTrue(content.isOwner)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = TripDetailsFakeTripRepository(
                trip = tripDetailsTripDto(
                    id = "trip-1",
                    ownerId = "o",
                    start = LocalDate.now().plusDays(1),
                    end = LocalDate.now().plusDays(2),
                ),
                members = listOf(tripDetailsMemberDto(id = "o", initials = "O")),
            ).apply { refreshTripsResult = Result.success(Unit) },
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "o")),
            tripId = "trip-1",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripMembersEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_contentAndRefreshSucceeds_when_onRefresh_then_keepsContentNotLoading() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "o",
                start = LocalDate.now().plusDays(1),
                end = LocalDate.now().plusDays(2),
            ),
            members = listOf(tripDetailsMemberDto(id = "o", initials = "O")),
        ).apply { refreshTripsResult = Result.success(Unit) }
        val viewModel = createViewModel(
            navigator = TripDetailsFakeNavigator(),
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "o")),
            tripId = "trip-1",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripMembersEvent.OnRefresh)
        advanceUntilIdle()

        // THEN
        val content = viewModel.state.value as? TripMembersState.Content
        assertNotNull(content)
        assertTrue(!content!!.isLoadingAction)
    }

    @Test
    fun given_refreshFails_when_onRefresh_then_emitsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "o",
                start = LocalDate.now().plusDays(1),
                end = LocalDate.now().plusDays(2),
            ),
            members = listOf(tripDetailsMemberDto(id = "o", initials = "O")),
        ).apply { refreshTripsResult = Result.failure(IOException("offline")) }
        val viewModel = createViewModel(
            navigator = TripDetailsFakeNavigator(),
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "o")),
            tripId = "trip-1",
        )
        advanceUntilIdle()
        val effects = mutableListOf<TripMembersEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }

        // WHEN
        viewModel.onEvent(TripMembersEvent.OnRefresh)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(1, effects.size)
        assertTrue(effects.single() is TripMembersEffect.ShowToastRes)
    }

    @Test
    fun given_otherMember_when_onRemoveClickSuccess_then_doesNotNavigate() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "owner-1",
                start = LocalDate.now().plusDays(1),
                end = LocalDate.now().plusDays(2),
            ),
            members = listOf(
                tripDetailsMemberDto(id = "owner-1", initials = "OW"),
                tripDetailsMemberDto(id = "guest-1", initials = "GT"),
            ),
        ).apply { refreshTripsResult = Result.success(Unit) }
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            tripId = "trip-1",
        )
        advanceUntilIdle()
        // WHEN
        viewModel.onEvent(TripMembersEvent.OnRemoveClick("guest-1"))
        advanceUntilIdle()

        // THEN
        assertEquals(0, navigator.destinations.size)
    }

    @Test
    fun given_selfMember_when_onRemoveClickSuccess_then_navigatesToTrips() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "owner-1",
                start = LocalDate.now().plusDays(1),
                end = LocalDate.now().plusDays(2),
            ),
            members = listOf(tripDetailsMemberDto(id = "owner-1", initials = "OW")),
        ).apply { refreshTripsResult = Result.success(Unit) }
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            tripId = "trip-1",
        )
        advanceUntilIdle()
        // WHEN
        viewModel.onEvent(TripMembersEvent.OnRemoveClick("owner-1"))
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.Trips })
    }

    @Test
    fun given_removeMemberFails_when_onRemoveClick_then_emitsMappedToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "owner-1",
                start = LocalDate.now().plusDays(1),
                end = LocalDate.now().plusDays(2),
            ),
            members = listOf(
                tripDetailsMemberDto(id = "owner-1", initials = "OW"),
                tripDetailsMemberDto(id = "guest-1", initials = "GT"),
            ),
        ).apply { refreshTripsResult = Result.success(Unit) }
        val viewModel = createViewModel(
            navigator = TripDetailsFakeNavigator(),
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            tripId = "trip-1",
        )
        advanceUntilIdle()
        tripRepository.removeMemberError = IOException("server error")
        val effects = mutableListOf<TripMembersEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }

        // WHEN
        viewModel.onEvent(TripMembersEvent.OnRemoveClick("guest-1"))
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(1, effects.size)
        assertTrue(effects.single() is TripMembersEffect.ShowToastRes)
    }

    private fun createViewModel(
        navigator: TripDetailsFakeNavigator,
        tripRepository: TripDetailsFakeTripRepository,
        userRepository: TripDetailsFakeUserRepository,
        tripId: String,
    ): TripMembersViewModel = TripMembersViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Destination.TripMembers.ARG_TRIP_ID to tripId)),
        appNavigator = navigator,
        tripRepository = tripRepository,
        userRepository = userRepository,
        apiCaller = apiCaller,
        uiErrorMapper = uiErrorMapper,
    )
}
