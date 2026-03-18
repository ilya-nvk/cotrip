package nvk.cotrip.ui.activity.details

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import nvk.cotrip.ui.activity.ActivityFakeTripRepository
import nvk.cotrip.ui.activity.activityDayDto
import nvk.cotrip.ui.activity.activityDto
import nvk.cotrip.ui.activity.activityTripDto
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActivityDetailsScreenComposeTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun given_contentState_when_screenRenders_then_displaysActivityTitle() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1", endDate = LocalDate.now().plusDays(5).toString())
        val activity = activityDto(id = "activity-1", dayId = "day-1", title = "Eiffel Tower")
        val day = activityDayDto(id = "day-1", tripId = "trip-1", activities = listOf(activity))
        val tripRepository = ActivityFakeTripRepository(trip = trip)
        tripRepository.setTrips(listOf(trip))
        val itineraryRepository = ActivityFakeItineraryRepository(initialTripId = "trip-1")
        itineraryRepository.setItinerary("trip-1", listOf(day))
        val viewModel = ActivityDetailsViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.ActivityDetails.ARG_ACTIVITY_ID to "activity-1")
            ),
            appNavigator = nvk.cotrip.ui.activity.ActivityFakeNavigator(),
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
        advanceUntilIdle()
        advanceUntilIdle()

        // WHEN
        composeRule.setContent {
            ActivityDetailsScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText("Eiffel Tower", substring = true).assertIsDisplayed()
    }
}
