package nvk.cotrip.ui.itinerary

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.repository.PendingTripCreationStore
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
class TripItineraryScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_filledState_when_screenRenders_then_rendersDayCityAndActivity() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val today = LocalDate.now()
        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.plusDays(1),
                    end = today.plusDays(2),
                )
            ),
            itineraryRepository = TripItineraryFakeRepository(
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
                            )
                        ),
                    )
                )
            ),
            requireCities = false,
        )

        // WHEN
        composeRule.setContent {
            TripItineraryScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(context.getString(R.string.itinerary_title), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.itinerary_day_title, 1), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Rome", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Colosseum", substring = true).assertIsDisplayed()
    }

    @Test
    fun given_requiredCitySelection_when_screenRenders_then_rendersBannerAndContinueControls() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val today = LocalDate.now()
        val viewModel = createViewModel(
            tripRepository = TripItineraryFakeTripRepository(
                trip = itineraryTripDto(
                    id = "trip-1",
                    start = today.plusDays(1),
                    end = today.plusDays(2),
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
            requireCities = true,
        )

        // WHEN
        composeRule.setContent {
            TripItineraryScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(context.getString(R.string.itinerary_city_setup_banner), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.itinerary_city_setup_remaining, 1), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.itinerary_city_setup_continue), substring = true).assertIsDisplayed()
    }

    private fun createViewModel(
        tripRepository: TripItineraryFakeTripRepository,
        itineraryRepository: TripItineraryFakeRepository,
        requireCities: Boolean,
    ): TripItineraryViewModel {
        val pendingStore = mockk<PendingTripCreationStore>()
        coEvery { pendingStore.setPendingTripId(any()) } returns Unit
        coEvery { pendingStore.clearPendingTripId(any()) } returns Unit

        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true

        return TripItineraryViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Destination.TripItinerary.ARG_TRIP_ID to "trip-1",
                    Destination.TripItinerary.ARG_REQUIRE_CITIES to requireCities,
                    Destination.TripItinerary.ARG_CREATION_FLOW to false,
                )
            ),
            appNavigator = TripItineraryFakeNavigator(),
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
            pendingTripCreationStore = pendingStore,
            apiCaller = ApiCaller(Json { ignoreUnknownKeys = true }),
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
