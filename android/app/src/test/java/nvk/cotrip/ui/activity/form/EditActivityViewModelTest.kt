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
class EditActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_activityExists_when_init_then_loadsActivityAndFillsState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val activity = activityDto(
            id = "activity-1",
            dayId = "day-1",
            title = "Louvre",
            timeText = "10:00",
            notes = "Book in advance",
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

        // THEN
        val state = viewModel.state.value
        assertEquals(ActivityFormMode.Edit, state.mode)
        assertEquals("activity-1", state.activityId)
        assertEquals("Louvre", state.title)
        assertEquals("10:00", state.timeText)
        assertEquals("Book in advance", state.notes)
        assertEquals("Paris", state.headerCity)
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
    fun given_validData_when_onPrimaryClick_then_updatesActivityAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val activity = activityDto(id = "activity-1", dayId = "day-1", title = "Original")
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

        // WHEN
        viewModel.onEvent(ActivityFormEvent.OnTitleChange("Updated title"))
        viewModel.onEvent(ActivityFormEvent.OnPrimaryClick)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, itineraryRepository.updateActivityCalls.size)
        assertEquals("activity-1", itineraryRepository.updateActivityCalls.single().first)
        assertEquals("Updated title", itineraryRepository.updateActivityCalls.single().second.title)
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_activityLoaded_when_onDeleteClick_then_deletesActivityAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
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

        // WHEN
        viewModel.onEvent(ActivityFormEvent.OnDeleteClick)
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
            trip = activityTripDto(id = "trip-1"),
        ).apply {
            setTrips(listOf(activityTripDto(id = "trip-1")))
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
    ): EditActivityViewModel = EditActivityViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(Destination.EditActivity.ARG_ACTIVITY_ID to "activity-1")
        ),
        appNavigator = navigator,
        tripRepository = tripRepository,
        itineraryRepository = itineraryRepository,
        apiCaller = apiCaller,
        uiErrorMapper = UiErrorMapper(networkStateProvider),
    )
}
