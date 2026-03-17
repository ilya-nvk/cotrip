package nvk.cotrip.ui.trip.details

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
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
class TripDetailsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_futureTrip_when_screenRenders_then_rendersMainSectionsAndForecastCard() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val today = LocalDate.now()
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(
                trip = tripDetailsTripDto(
                    id = "trip-1",
                    ownerId = "owner-1",
                    title = "Spring Rome",
                    start = today.plusDays(1),
                    end = today.plusDays(3),
                ),
                members = listOf(
                    tripDetailsMemberDto(id = "owner-1", initials = "OW"),
                    tripDetailsMemberDto(id = "guest-1", initials = "GT"),
                ),
            ),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            ideaRepository = TripDetailsFakeIdeaRepository(
                ideas = listOf(tripDetailsIdeaDto(id = "idea-1"))
            ),
            expenseRepository = TripDetailsFakeExpenseRepository(
                expenses = listOf(tripDetailsExpenseDto(id = "expense-1", amount = 10.0))
            ),
            itineraryRepository = TripDetailsFakeItineraryRepository(
                days = listOf(
                    tripDetailsDayDto(
                        id = "day-1",
                        dayNumber = 1,
                        date = today.plusDays(1),
                        city = "Rome",
                    )
                )
            ),
            weatherRepository = TripDetailsFakeWeatherRepository().apply {
                setCachedResponse(
                    city = "Rome",
                    response = tripDetailsWeatherResponse(city = "Rome", date = today.plusDays(1)),
                )
            },
        )

        // WHEN
        composeRule.setContent {
            TripDetailsScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText("Spring Rome", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.trip_details_travelers), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.trip_details_weather), substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.trip_details_view_full_forecast), substring = true).assertCountEquals(1)
    }

    @Test
    fun given_pastTrip_when_screenRenders_then_hidesWeatherIdeasAndPrimaryCta() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val today = LocalDate.now()
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(
                trip = tripDetailsTripDto(
                    id = "trip-1",
                    ownerId = "owner-1",
                    title = "Past Rome",
                    start = today.minusDays(5),
                    end = today.minusDays(1),
                )
            ),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            ideaRepository = TripDetailsFakeIdeaRepository(
                ideas = listOf(tripDetailsIdeaDto(id = "idea-1"))
            ),
            expenseRepository = TripDetailsFakeExpenseRepository(
                expenses = listOf(tripDetailsExpenseDto(id = "expense-1", amount = 12.0))
            ),
            itineraryRepository = TripDetailsFakeItineraryRepository(
                days = listOf(
                    tripDetailsDayDto(
                        id = "day-1",
                        dayNumber = 1,
                        date = today.minusDays(5),
                        city = "Rome",
                    )
                )
            ),
            weatherRepository = TripDetailsFakeWeatherRepository().apply {
                setCachedResponse(
                    city = "Rome",
                    response = tripDetailsWeatherResponse(city = "Rome", date = today.minusDays(5)),
                )
            },
        )

        // WHEN
        composeRule.setContent {
            TripDetailsScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText("Past Rome", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.trip_details_travelers), substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.trip_details_weather), substring = true).assertCountEquals(0)
    }

    private fun createViewModel(
        tripRepository: TripDetailsFakeTripRepository,
        userRepository: TripDetailsFakeUserRepository,
        ideaRepository: TripDetailsFakeIdeaRepository,
        expenseRepository: TripDetailsFakeExpenseRepository,
        itineraryRepository: TripDetailsFakeItineraryRepository,
        weatherRepository: TripDetailsFakeWeatherRepository,
    ): TripDetailsViewModel {
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        return TripDetailsViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.TripDetails.ARG_TRIP_ID to "trip-1")
            ),
            appContext = ApplicationProvider.getApplicationContext(),
            appNavigator = TripDetailsFakeNavigator(),
            tripRepository = tripRepository,
            userRepository = userRepository,
            ideaRepository = ideaRepository,
            expenseRepository = expenseRepository,
            itineraryRepository = itineraryRepository,
            weatherRepository = weatherRepository,
            apiCaller = ApiCaller(Json { ignoreUnknownKeys = true }),
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
