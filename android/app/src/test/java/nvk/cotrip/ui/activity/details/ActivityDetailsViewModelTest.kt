package nvk.cotrip.ui.activity.details

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
import nvk.cotrip.ui.activity.activityDto
import nvk.cotrip.ui.activity.activityTripDto
import nvk.cotrip.ui.common.UiErrorMapper
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
class ActivityDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_activityExists_when_init_then_loadsActivityAndShowsContent() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1", endDate = LocalDate.now().plusDays(5).toString())
        val activity = activityDto(
            id = "activity-1",
            dayId = "day-1",
            title = "Eiffel Tower",
            timeText = "14:00",
            link = "https://example.com",
        )
        val day = activityDayDto(
            id = "day-1",
            tripId = "trip-1",
            date = LocalDate.now().toString(),
            dayNumber = 1,
            city = "Paris",
            activities = listOf(activity),
        )
        val tripRepository = ActivityFakeTripRepository(trip = trip)
        tripRepository.setTrips(listOf(trip))
        val itineraryRepository = ActivityFakeItineraryRepository(initialTripId = "trip-1")
        itineraryRepository.setItinerary("trip-1", listOf(day))
        val viewModel = createViewModel(
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
        )
        // WHEN
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertTrue(state is ActivityDetailsState.Content)
        val content = state as ActivityDetailsState.Content
        assertEquals("Eiffel Tower", content.title)
        assertEquals("activity-1", content.activityId)
        assertEquals("day-1", content.dayId)
        assertEquals("Paris", content.city)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ActivityFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ActivityDetailsEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_onEditClick_then_navigatesToEditActivity() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ActivityFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ActivityDetailsEvent.OnEditClick)

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.EditActivity })
        val edit = navigator.destinations.filterIsInstance<Destination.EditActivity>().single()
        assertEquals("activity-1", edit.activityId)
    }

    @Test
    fun given_contentShown_when_onRefresh_then_succeeds() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ActivityDetailsEvent.OnRefresh)
        advanceUntilIdle()

        // THEN
        assertTrue(viewModel.state.value is ActivityDetailsState.Content)
    }

    @Test
    fun given_activityLoaded_when_onDeleteClick_then_deletesAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1", endDate = LocalDate.now().plusDays(5).toString())
        val activity = activityDto(id = "activity-1", dayId = "day-1", title = "To delete")
        val day = activityDayDto(id = "day-1", tripId = "trip-1", activities = listOf(activity))
        val navigator = ActivityFakeNavigator()
        val tripRepository = ActivityFakeTripRepository(trip = trip)
        tripRepository.setTrips(listOf(trip))
        val itineraryRepository = ActivityFakeItineraryRepository(initialTripId = "trip-1")
        itineraryRepository.setItinerary("trip-1", listOf(day))
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
        )
        advanceUntilIdle()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ActivityDetailsEvent.OnDeleteClick)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, itineraryRepository.deleteActivityCalls.size)
        assertEquals("activity-1", itineraryRepository.deleteActivityCalls.single())
        assertEquals(1, navigator.popCalls)
    }

    private fun createViewModel(
        navigator: ActivityFakeNavigator = ActivityFakeNavigator(),
        tripRepository: ActivityFakeTripRepository = ActivityFakeTripRepository(
            trip = activityTripDto(id = "trip-1", endDate = LocalDate.now().plusDays(5).toString()),
        ).apply {
            setTrips(listOf(activityTripDto(id = "trip-1", endDate = LocalDate.now().plusDays(5).toString())))
        },
        itineraryRepository: ActivityFakeItineraryRepository = ActivityFakeItineraryRepository(
            initialDays = listOf(
                activityDayDto(
                    id = "day-1",
                    tripId = "trip-1",
                    date = LocalDate.now().toString(),
                    activities = listOf(activityDto(id = "activity-1", dayId = "day-1", title = "Test")),
                ),
            ),
            initialTripId = "trip-1",
        ),
    ): ActivityDetailsViewModel = ActivityDetailsViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(Destination.ActivityDetails.ARG_ACTIVITY_ID to "activity-1")
        ),
        appNavigator = navigator,
        tripRepository = tripRepository,
        itineraryRepository = itineraryRepository,
        apiCaller = apiCaller,
        uiErrorMapper = UiErrorMapper(networkStateProvider),
    )
}
