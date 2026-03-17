package nvk.cotrip.ui.trip.form

import android.app.Application
import io.mockk.coEvery
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
import nvk.cotrip.data.repository.PendingTripCreationStore
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
import nvk.cotrip.ui.trip.details.TripDetailsFakeTripRepository
import nvk.cotrip.ui.trip.details.tripDetailsTripDto
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
class CreateTripViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)

    @Test
    fun given_screenOpen_when_onCloseClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)

        // WHEN
        viewModel.onEvent(TripFormEvent.OnCloseClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_onCancelClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)

        // WHEN
        viewModel.onEvent(TripFormEvent.OnCancelClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_nameAndDatesSet_when_onNameChange_then_updatesStateAndRecomputesCanSubmit() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnNameChange("My Trip"))
        advanceUntilIdle()

        // THEN
        assertEquals("My Trip", viewModel.state.value.name)
        assertFalse(viewModel.state.value.canSubmit)

        // WHEN
        val start = LocalDate.now()
        val end = LocalDate.now()
        viewModel.onEvent(TripFormEvent.OnStartDateSelected(start))
        viewModel.onEvent(TripFormEvent.OnEndDateSelected(end))
        advanceUntilIdle()

        // THEN
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun given_invalidStartDate_when_onStartDateSelected_then_emitsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        val effects = mutableListOf<TripFormEffect>()
        launch { viewModel.effects.take(1).toList(effects) }
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnStartDateSelected(LocalDate.now().minusDays(1)))
        advanceUntilIdle()

        // THEN
        assertTrue(effects.isNotEmpty())
        assertTrue(effects.any { it is TripFormEffect.ShowToastRes && it.resId == R.string.trip_form_start_date_range_toast })
    }

    @Test
    fun given_invalidEndDate_when_onEndDateSelected_then_emitsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        viewModel.onEvent(TripFormEvent.OnStartDateSelected(LocalDate.now()))
        advanceUntilIdle()
        val effects = mutableListOf<TripFormEffect>()
        launch { viewModel.effects.take(1).toList(effects) }
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnEndDateSelected(LocalDate.now().minusDays(1)))
        advanceUntilIdle()

        // THEN
        assertTrue(effects.isNotEmpty())
        assertTrue(effects.any { it is TripFormEffect.ShowToastRes && it.resId == R.string.trip_form_end_date_range_toast })
    }

    @Test
    fun given_validData_when_onPrimaryActionClick_then_createsTripAndNavigatesToItinerary() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val tripRepo = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                start = LocalDate.now(),
                end = LocalDate.now().plusDays(1),
            ),
        )
        val viewModel = createViewModel(navigator = navigator, tripRepository = tripRepo)
        viewModel.onEvent(TripFormEvent.OnNameChange("New Trip"))
        viewModel.onEvent(TripFormEvent.OnStartDateSelected(LocalDate.now()))
        viewModel.onEvent(TripFormEvent.OnEndDateSelected(LocalDate.now().plusDays(1)))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canSubmit)
        val effects = mutableListOf<TripFormEffect>()
        launch { viewModel.effects.take(1).toList(effects) }
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripFormEvent.OnPrimaryActionClick)
        advanceUntilIdle()

        // THEN
        assertTrue("Expected navigate to TripItinerary, got: ${navigator.destinations}", navigator.destinations.isNotEmpty())
        assertTrue(navigator.destinations.any { it is nvk.cotrip.ui.navigation.Destination.TripItinerary })
        assertTrue(effects.any { it is TripFormEffect.ShowToastRes })
    }

    @Test
    fun given_invalidData_when_onPrimaryActionClick_then_doesNotNavigate() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        viewModel.onEvent(TripFormEvent.OnNameChange(""))
        viewModel.onEvent(TripFormEvent.OnStartDateSelected(LocalDate.now()))
        viewModel.onEvent(TripFormEvent.OnEndDateSelected(LocalDate.now()))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canSubmit)

        // WHEN
        viewModel.onEvent(TripFormEvent.OnPrimaryActionClick)
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.isEmpty())
    }

    private fun createViewModel(
        navigator: TripDetailsFakeNavigator = TripDetailsFakeNavigator(),
        tripRepository: TripDetailsFakeTripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                start = LocalDate.now(),
                end = LocalDate.now().plusDays(1),
            ),
        ),
        imageUploadRepository: FakeImageUploadRepository = FakeImageUploadRepository(),
        pendingTripCreationStore: PendingTripCreationStore = mockPendingStore(),
    ): CreateTripViewModel {
        coEvery { pendingTripCreationStore.getPendingTripId() } returns null
        coEvery { pendingTripCreationStore.setPendingTripId(any()) } returns Unit
        coEvery { pendingTripCreationStore.clearPendingTripId(any()) } returns Unit
        return CreateTripViewModel(
            appNavigator = navigator,
            tripRepository = tripRepository,
            imageUploadRepository = imageUploadRepository,
            pendingTripCreationStore = pendingTripCreationStore,
            apiCaller = apiCaller,
            uiErrorMapper = uiErrorMapper,
        )
    }

    private fun mockPendingStore(): PendingTripCreationStore {
        val store = mockk<PendingTripCreationStore>()
        coEvery { store.getPendingTripId() } returns null
        coEvery { store.setPendingTripId(any()) } returns Unit
        coEvery { store.clearPendingTripId(any()) } returns Unit
        return store
    }
}
