package nvk.cotrip.ui.activity.form

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
import nvk.cotrip.ui.activity.ActivityFakeNavigator
import nvk.cotrip.ui.activity.ActivityFakeTripRepository
import nvk.cotrip.ui.activity.activityDayDto
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
class ActivityFormScreenComposeTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun given_createForm_when_screenRenders_then_displaysActivityTitleSection() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val day = activityDayDto(id = "day-1", tripId = "trip-1")
        val itineraryRepository = ActivityFakeItineraryRepository(
            initialDays = listOf(day),
            initialTripId = "trip-1",
        )
        val viewModel = CreateActivityViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Destination.CreateActivity.ARG_TRIP_ID to "trip-1")),
            appNavigator = ActivityFakeNavigator(),
            tripRepository = ActivityFakeTripRepository(trip = trip),
            itineraryRepository = itineraryRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
        advanceUntilIdle()

        // WHEN
        composeRule.setContent {
            CreateActivityScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText("ACTIVITY TITLE", substring = true).assertIsDisplayed()
    }
}
