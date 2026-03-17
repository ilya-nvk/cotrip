package nvk.cotrip.ui.activity.form

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.ui.activity.ActivityFakeItineraryRepository
import nvk.cotrip.ui.activity.ActivityFakeNavigator
import nvk.cotrip.ui.activity.ActivityFakeTripRepository
import nvk.cotrip.ui.activity.activityDayDto
import nvk.cotrip.ui.activity.activityTripDto
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class CreateActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_tripAndDayExist_when_init_then_loadsTripMetaAndFillsState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val day = activityDayDto(id = "day-1", tripId = "trip-1", dayNumber = 1, city = "Paris")
        val tripRepository = ActivityFakeTripRepository(trip = trip)
        val itineraryRepository = ActivityFakeItineraryRepository(
            initialDays = listOf(day),
            initialTripId = "trip-1",
        )
        val viewModel = createViewModel(
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertEquals(ActivityFormMode.Create, state.mode)
        assertNotNull(state.headerDayNumber)
        assertEquals(1, state.headerDayNumber)
        assertEquals("Paris", state.headerCity)
        assertNotNull(state.tripStartDate)
        assertNotNull(state.tripEndDate)
        assertTrue(state.currencySymbol.isNotBlank())
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ActivityFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ActivityFormEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_emptyTitle_when_onPrimaryClick_then_doesNotCreateActivity() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val day = activityDayDto(id = "day-1", tripId = "trip-1")
        val itineraryRepository = ActivityFakeItineraryRepository(
            initialDays = listOf(day),
            initialTripId = "trip-1",
        )
        val viewModel = createViewModel(
            tripRepository = ActivityFakeTripRepository(trip = trip),
            itineraryRepository = itineraryRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ActivityFormEvent.OnPrimaryClick)
        advanceUntilIdle()

        // THEN
        assertEquals(0, itineraryRepository.createActivityCalls.size)
    }

    @Test
    fun given_validData_when_onPrimaryClick_then_createsActivityAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val day = activityDayDto(id = "day-1", tripId = "trip-1")
        val navigator = ActivityFakeNavigator()
        val itineraryRepository = ActivityFakeItineraryRepository(
            initialDays = listOf(day),
            initialTripId = "trip-1",
        )
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = ActivityFakeTripRepository(trip = trip),
            itineraryRepository = itineraryRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ActivityFormEvent.OnTitleChange("Museum visit"))
        viewModel.onEvent(ActivityFormEvent.OnPrimaryClick)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, itineraryRepository.createActivityCalls.size)
        assertEquals("day-1", itineraryRepository.createActivityCalls.single().first)
        assertEquals("Museum visit", itineraryRepository.createActivityCalls.single().second.title)
        assertEquals(1, navigator.popCalls)
    }

    private fun createViewModel(
        navigator: ActivityFakeNavigator = ActivityFakeNavigator(),
        tripRepository: ActivityFakeTripRepository = ActivityFakeTripRepository(
            trip = activityTripDto(id = "trip-1"),
        ),
        itineraryRepository: ActivityFakeItineraryRepository = ActivityFakeItineraryRepository(
            initialDays = listOf(
                activityDayDto(id = "day-1", tripId = "trip-1", date = LocalDate.now().toString()),
            ),
            initialTripId = "trip-1",
        ),
    ): CreateActivityViewModel = CreateActivityViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(Destination.CreateActivity.ARG_TRIP_ID to "trip-1")
        ),
        appNavigator = navigator,
        tripRepository = tripRepository,
        itineraryRepository = itineraryRepository,
        apiCaller = apiCaller,
        uiErrorMapper = UiErrorMapper(networkStateProvider),
    )
}
