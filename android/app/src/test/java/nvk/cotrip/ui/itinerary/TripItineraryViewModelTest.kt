package nvk.cotrip.ui.itinerary

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.repository.PendingTripCreationStore
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
@Config(application = Application::class)
class TripItineraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_activitiesExist_when_init_then_mapsFilledModeAndRows() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val itineraryRepository = TripItineraryFakeRepository(
            days = listOf(
                itineraryDayDto(
                    id = "day-1",
                    dayNumber = 1,
                    date = today.plusDays(1),
                    city = "Rome",
                    activities = listOf(
                        itineraryActivityDto(
                            id = "act-1",
                            dayId = "day-1",
                            title = "Colosseum",
                            order = 0,
                            location = "Rome",
                            cost = 18.0,
                        )
                    ),
                )
            )
        )
        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.plusDays(1),
                    end = today.plusDays(2),
                )
            ),
            itineraryRepository = itineraryRepository,
            pendingTripCreationStore = mockPendingStore(),
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertEquals(ItineraryMode.Filled, state.mode)
        assertFalse(state.isPastTrip)
        assertEquals(1, state.days.size)
        assertEquals("Rome", state.days.first().city)
        assertEquals("Colosseum", state.days.first().activities.single().title)
    }

    @Test
    fun given_pendingCities_when_completeRequiredSelection_then_setsInlineErrorAndStays() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val navigator = TripItineraryFakeNavigator()
        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.plusDays(1),
                    end = today.plusDays(3),
                )
            ),
            itineraryRepository = TripItineraryFakeRepository(
                days = listOf(
                    itineraryDayDto(
                        id = "day-1",
                        dayNumber = 1,
                        date = today.plusDays(1),
                        city = null,
                    )
                )
            ),
            pendingTripCreationStore = mockPendingStore(),
            navigator = navigator,
            requireCities = true,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripItineraryEvent.OnCompleteRequiredCitySelection)
        advanceUntilIdle()

        // THEN
        assertEquals(R.string.itinerary_city_setup_required_toast, viewModel.state.value.inlineErrorRes)
        assertTrue(navigator.destinations.isEmpty())
    }

    @Test
    fun given_pendingCity_when_selectCityAndComplete_then_reducesPendingCountAndNavigatesToTripDetails() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val pendingStore = mockPendingStore()
        val navigator = TripItineraryFakeNavigator()
        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.plusDays(1),
                    end = today.plusDays(3),
                )
            ),
            itineraryRepository = TripItineraryFakeRepository(
                days = listOf(
                    itineraryDayDto(
                        id = "day-1",
                        dayNumber = 1,
                        date = today.plusDays(1),
                        city = null,
                    )
                )
            ),
            pendingTripCreationStore = pendingStore,
            navigator = navigator,
            requireCities = true,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripItineraryEvent.OnChooseCityClick("day-1"))
        viewModel.onEvent(
            TripItineraryEvent.OnCitySelected(
                CitySuggestionUi(
                    name = "Rome",
                    providerId = "rome-id",
                    lat = 41.9,
                    lon = 12.5,
                    fullText = "Rome, Italy",
                )
            )
        )
        advanceUntilIdle()

        val updated = viewModel.state.value
        assertEquals(0, updated.pendingCitySelectionCount)
        assertEquals("Rome", updated.days.first().city)

        // WHEN
        viewModel.onEvent(TripItineraryEvent.OnCompleteRequiredCitySelection)
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.contains(Destination.TripDetails("trip-1")))
        coVerify(atLeast = 1) { pendingStore.clearPendingTripId("trip-1") }
    }

    @Test
    fun given_creationFlow_when_onBackClick_then_deletesTripAndPopsBack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val tripRepository = TripItineraryFakeTripRepository(
            trip = itineraryTripDto(
                id = "trip-1",
                start = today.plusDays(1),
                end = today.plusDays(2),
            )
        )
        val pendingStore = mockPendingStore()
        val navigator = TripItineraryFakeNavigator()
        val viewModel = createViewModel(
            tripRepository = tripRepository,
            itineraryRepository = TripItineraryFakeRepository(),
            pendingTripCreationStore = pendingStore,
            navigator = navigator,
            creationFlow = true,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripItineraryEvent.OnBackClick)
        advanceUntilIdle()

        // THEN
        assertEquals(listOf("trip-1"), tripRepository.deletedTripIds)
        assertEquals(1, navigator.popCalls)
        coVerify(atLeast = 1) { pendingStore.setPendingTripId("trip-1") }
        coVerify(atLeast = 1) { pendingStore.clearPendingTripId("trip-1") }
    }

    @Test
    fun given_refreshFails_when_userRefresh_then_emitsToastAndStopsRefreshing() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val itineraryRepository = TripItineraryFakeRepository(
            days = listOf(
                itineraryDayDto(
                    id = "day-1",
                    dayNumber = 1,
                    date = today.plusDays(1),
                    city = "Rome",
                )
            )
        )
        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.plusDays(1),
                    end = today.plusDays(2),
                )
            ),
            itineraryRepository = itineraryRepository,
            pendingTripCreationStore = mockPendingStore(),
        )
        advanceUntilIdle()
        itineraryRepository.refreshResult = Result.failure(IOException("offline"))

        val effects = mutableListOf<TripItineraryEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }

        // WHEN
        viewModel.onEvent(TripItineraryEvent.OnUserRefresh)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            TripItineraryEffect.ShowToastRes(R.string.common_error_server_unreachable),
            effects.single(),
        )
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun given_pastTrip_when_chooseCityOrAddActivity_then_blocksAndDoesNotNavigate() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val navigator = TripItineraryFakeNavigator()
        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.minusDays(5),
                    end = today.minusDays(1),
                )
            ),
            itineraryRepository = TripItineraryFakeRepository(
                days = listOf(
                    itineraryDayDto(
                        id = "day-1",
                        dayNumber = 1,
                        date = today.minusDays(5),
                        city = "Rome",
                    )
                )
            ),
            pendingTripCreationStore = mockPendingStore(),
            navigator = navigator,
        )
        advanceUntilIdle()

        viewModel.onEvent(TripItineraryEvent.OnChooseCityClick("day-1"))
        // WHEN
        viewModel.onEvent(TripItineraryEvent.OnAddActivityClick)
        advanceUntilIdle()

        // THEN
        assertNull(viewModel.state.value.cityPicker)
        assertTrue(navigator.destinations.isEmpty())
    }

    @Test
    fun given_cityQuerySearchFailure_when_onCityQueryChange_then_fallsBackToLocalSuggestions() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val itineraryRepository = TripItineraryFakeRepository(
            days = listOf(
                itineraryDayDto(
                    id = "day-1",
                    dayNumber = 1,
                    date = today.plusDays(1),
                    city = null,
                    lat = null,
                    lon = null,
                ),
                itineraryDayDto(
                    id = "day-2",
                    dayNumber = 2,
                    date = today.plusDays(2),
                    city = "Paris",
                    lat = 48.85,
                    lon = 2.35,
                ),
            )
        )
        itineraryRepository.searchResults = listOf(
            CitySuggestionDto(
                name = "Not used",
                providerId = "x",
                lat = 0.0,
                lon = 0.0,
                fullText = "Not used",
            )
        )
        itineraryRepository.searchError = IOException("search failed")

        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.plusDays(1),
                    end = today.plusDays(3),
                )
            ),
            itineraryRepository = itineraryRepository,
            pendingTripCreationStore = mockPendingStore(),
        )
        advanceUntilIdle()

        viewModel.onEvent(TripItineraryEvent.OnChooseCityClick("day-1"))
        // WHEN
        viewModel.onEvent(TripItineraryEvent.OnCityQueryChange("Par"))
        advanceTimeBy(350)
        advanceUntilIdle()

        // THEN
        val picker = viewModel.state.value.cityPicker
        assertEquals(1, picker?.suggestions?.size)
        assertEquals("Paris", picker?.suggestions?.first()?.name)
        assertFalse(picker?.isSearching ?: true)
    }

    private fun createViewModel(
        tripRepository: TripItineraryFakeTripRepository,
        itineraryRepository: TripItineraryFakeRepository,
        pendingTripCreationStore: PendingTripCreationStore,
        navigator: TripItineraryFakeNavigator = TripItineraryFakeNavigator(),
        requireCities: Boolean = false,
        creationFlow: Boolean = false,
    ): TripItineraryViewModel {
        return TripItineraryViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Destination.TripItinerary.ARG_TRIP_ID to "trip-1",
                    Destination.TripItinerary.ARG_REQUIRE_CITIES to requireCities,
                    Destination.TripItinerary.ARG_CREATION_FLOW to creationFlow,
                )
            ),
            appNavigator = navigator,
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
            pendingTripCreationStore = pendingTripCreationStore,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }

    private fun mockPendingStore(): PendingTripCreationStore {
        val store = mockk<PendingTripCreationStore>()
        coEvery { store.setPendingTripId(any()) } returns Unit
        coEvery { store.clearPendingTripId(any()) } returns Unit
        return store
    }
}
