package nvk.cotrip.ui.forecast

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripForecastScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_contentState_when_cityClick_then_rendersForecastAndOpensCityPicker() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val weatherTitle = context.getString(R.string.weather_forecast_title)
        val pickerTitle = context.getString(R.string.itinerary_choose_city_title)
        val viewModel = createViewModel(
            itineraryRepository = FakeItineraryRepository(
                initial = listOf(
                    dayDto(id = "day-1", dayNumber = 1, city = "Rome", lat = 41.9, lon = 12.5),
                    dayDto(id = "day-2", dayNumber = 2, city = "Paris", lat = 48.9, lon = 2.3),
                )
            ),
            weatherRepository = FakeWeatherRepository().apply {
                setResponse("Rome", weatherResponse())
                setResponse("Paris", weatherResponse(source = "Meteo"))
            },
        )

        // WHEN
        composeRule.setContent {
            TripForecastScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(weatherTitle, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Rome", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Clear sky", substring = true).assertIsDisplayed()

        // WHEN
        composeRule.onNodeWithText("Rome", substring = true).performClick()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(pickerTitle, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Paris", substring = true).assertIsDisplayed()
    }

    @Test
    fun given_noCityState_when_screenRenders_then_rendersCoverageAndEmptyCard() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val cityMissing = context.getString(R.string.trip_forecast_coverage_city_missing)
        val emptyText = context.getString(R.string.weather_forecast_empty)
        val chooseCity = context.getString(R.string.weather_forecast_city_missing)
        val viewModel = createViewModel(
            itineraryRepository = FakeItineraryRepository(
                initial = listOf(
                    dayDto(id = "day-1", dayNumber = 1, city = "Rome", lat = null, lon = null)
                )
            ),
            weatherRepository = FakeWeatherRepository(),
        )

        // WHEN
        composeRule.setContent {
            TripForecastScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(chooseCity, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(cityMissing, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(emptyText, substring = true).assertIsDisplayed()
    }

    private fun createViewModel(
        itineraryRepository: FakeItineraryRepository,
        weatherRepository: FakeWeatherRepository,
    ): TripForecastViewModel {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true

        return TripForecastViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.TripForecast.ARG_TRIP_ID to "trip-1")
            ),
            appContext = appContext,
            appNavigator = FakeNavigator(),
            tripRepository = FakeTripRepository().apply { setTrip(tripDto(id = "trip-1")) },
            itineraryRepository = itineraryRepository,
            weatherRepository = weatherRepository,
            apiCaller = ApiCaller(Json { ignoreUnknownKeys = true }),
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
