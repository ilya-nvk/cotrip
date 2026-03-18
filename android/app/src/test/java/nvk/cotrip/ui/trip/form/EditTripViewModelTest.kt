package nvk.cotrip.ui.trip.form

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
import nvk.cotrip.ui.trip.details.TripDetailsFakeTripRepository
import nvk.cotrip.ui.trip.details.TripDetailsFakeUserRepository
import nvk.cotrip.ui.trip.details.tripDetailsTripDto
import nvk.cotrip.ui.trip.details.tripDetailsUserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class EditTripViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripId = "trip-edit-1"
    private val ownerId = "owner-1"
    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)

    @Test
    fun given_tripExists_when_init_then_loadsTripAndFillsState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(3)
        val trip = tripDetailsTripDto(id = tripId, ownerId = ownerId, title = "Rome", start = start, end = end)
        val tripRepo = TripDetailsFakeTripRepository(trip = trip)
        val userRepo = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId))
        val viewModel = createViewModel(tripRepository = tripRepo, userRepository = userRepo)

        // WHEN
        advanceUntilIdle()

        // THEN
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Rome", viewModel.state.value.name)
        assertEquals(start, viewModel.state.value.startDate)
        assertEquals(end, viewModel.state.value.endDate)
    }

    @Test
    fun given_screenOpen_when_onCloseClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnCloseClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_validData_when_onPrimaryActionClick_then_callsUpdateTripAndShowsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, ownerId = ownerId, title = "Trip", start = start, end = end)
        val tripRepo = TripDetailsFakeTripRepository(trip = trip)
        val userRepo = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId))
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            tripRepository = tripRepo,
            userRepository = userRepo,
            navigator = navigator,
        )
        advanceUntilIdle()
        viewModel.onEvent(TripFormEvent.OnNameChange("Updated Title"))
        advanceUntilIdle()
        val effects = mutableListOf<TripFormEffect>()
        launch { viewModel.effects.take(1).toList(effects) }
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnPrimaryActionClick)
        advanceUntilIdle()

        // THEN
        assertTrue(effects.any { it is TripFormEffect.ShowToastRes && it.resId == R.string.edit_trip_saved_toast })
        assertTrue(navigator.popCalls >= 1)
    }

    @Test
    fun given_tripLoaded_when_onArchiveClickSuccess_then_showsToastAndCloses() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = tripDetailsTripDto(
            id = tripId,
            ownerId = ownerId,
            title = "Trip",
            start = LocalDate.now(),
            end = LocalDate.now().plusDays(1),
        )
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId)),
            navigator = navigator,
        )
        advanceUntilIdle()
        val effects = mutableListOf<TripFormEffect>()
        launch { viewModel.effects.take(1).toList(effects) }
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnArchiveClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
        assertTrue(effects.any { it is TripFormEffect.ShowToastRes && it.resId == R.string.edit_trip_archived_toast })
    }

    @Test
    fun given_tripLoaded_when_onDeleteClickSuccess_then_navigatesToTrips() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = tripDetailsTripDto(
            id = tripId,
            ownerId = ownerId,
            title = "Trip",
            start = LocalDate.now(),
            end = LocalDate.now().plusDays(1),
        )
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId)),
            navigator = navigator,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnDeleteClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.destinations.size)
        assertTrue(navigator.destinations.single() is Destination.Trips)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(
            mapOf(Destination.EditTrip.ARG_TRIP_ID to tripId)
        ),
        navigator: TripDetailsFakeNavigator = TripDetailsFakeNavigator(),
        tripRepository: TripDetailsFakeTripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = tripId,
                ownerId = ownerId,
                title = "Trip",
                start = LocalDate.now(),
                end = LocalDate.now().plusDays(1),
            ),
        ),
        userRepository: TripDetailsFakeUserRepository = TripDetailsFakeUserRepository(
            me = tripDetailsUserDto(id = ownerId),
        ),
        imageUploadRepository: FakeImageUploadRepository = FakeImageUploadRepository(),
    ): EditTripViewModel = EditTripViewModel(
        savedStateHandle = savedStateHandle,
        appNavigator = navigator,
        tripRepository = tripRepository,
        userRepository = userRepository,
        imageUploadRepository = imageUploadRepository,
        apiCaller = apiCaller,
        uiErrorMapper = uiErrorMapper,
    )
}
