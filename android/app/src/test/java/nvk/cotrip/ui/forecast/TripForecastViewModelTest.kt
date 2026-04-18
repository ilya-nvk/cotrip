package nvk.cotrip.ui.forecast

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
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
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripForecastViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_validCity_when_init_then_loadsContentAndRefreshesWeather() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val navigator = FakeNavigator()
        val tripRepository = FakeTripRepository().apply {
            setTrip(tripDto(id = "trip-1"))
        }
        val itineraryRepository = FakeItineraryRepository(
            initial = listOf(dayDto(id = "day-1", dayNumber = 1, city = "Rome", lat = 41.9, lon = 12.5))
        )
        val weatherRepository = FakeWeatherRepository().apply {
            setResponse("Rome", weatherResponse())
        }

        val viewModel = createViewModel(
            appContext = appContext,
            navigator = navigator,
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
            weatherRepository = weatherRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as TripForecastState.Content
        assertEquals("Rome", state.city)
        assertEquals(listOf(WeatherCityOption(key = "Rome", label = "Rome")), state.cityOptions)
        assertTrue(state.days.isNotEmpty())
        assertTrue(state.coverageMessage == null)
        assertEquals(1, weatherRepository.refreshRequests.size)
        assertEquals("Rome", weatherRepository.refreshRequests.single().city)
    }

    @Test
    fun given_withoutSelectableCity_when_init_then_showsCoverageMessageAndSkipsWeatherRefresh() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val weatherRepository = FakeWeatherRepository()

        val viewModel = createViewModel(
            appContext = appContext,
            navigator = FakeNavigator(),
            tripRepository = FakeTripRepository(),
            itineraryRepository = FakeItineraryRepository(
                initial = listOf(
                    dayDto(id = "day-1", dayNumber = 1, city = "Rome", lat = null, lon = null),
                    dayDto(id = "day-2", dayNumber = 2, city = null, lat = null, lon = null),
                )
            ),
            weatherRepository = weatherRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as TripForecastState.Content
        assertEquals("", state.city)
        assertTrue(state.days.isEmpty())
        assertEquals(
            appContext.getString(R.string.trip_forecast_coverage_city_missing),
            state.coverageMessage,
        )
        assertTrue(weatherRepository.refreshRequests.isEmpty())
    }

    @Test
    fun given_contentShown_when_cityPickerEvents_then_toggleSheetAndRefreshForSelectedCity() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val weatherRepository = FakeWeatherRepository().apply {
            setResponse("Rome", weatherResponse())
            setResponse("Paris", weatherResponse(source = "Meteo"))
        }
        val itineraryRepository = FakeItineraryRepository(
            initial = listOf(
                dayDto(id = "day-1", dayNumber = 1, city = "Rome", lat = 41.9, lon = 12.5),
                dayDto(id = "day-2", dayNumber = 2, city = "Paris", lat = 48.9, lon = 2.3),
            )
        )
        val viewModel = createViewModel(
            appContext = appContext,
            navigator = FakeNavigator(),
            tripRepository = FakeTripRepository(),
            itineraryRepository = itineraryRepository,
            weatherRepository = weatherRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripForecastEvent.OnCityClick)
        // THEN
        assertTrue((viewModel.state.value as TripForecastState.Content).isCityPickerVisible)

        viewModel.onEvent(TripForecastEvent.OnDismissCityPicker)
        assertFalse((viewModel.state.value as TripForecastState.Content).isCityPickerVisible)

        viewModel.onEvent(TripForecastEvent.OnCityClick)
        viewModel.onEvent(TripForecastEvent.OnCitySelected("Paris"))
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as TripForecastState.Content
        assertEquals("Paris", state.city)
        assertFalse(state.isCityPickerVisible)
        assertTrue(weatherRepository.refreshRequests.count { it.city == "Paris" } >= 1)
    }

    @Test
    fun given_userRefreshFailure_when_onUserRefresh_then_emitsToastAndResetsRefreshingFlag() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val weatherRepository = FakeWeatherRepository().apply {
            setResponse("Rome", weatherResponse())
        }
        val viewModel = createViewModel(
            appContext = appContext,
            navigator = FakeNavigator(),
            tripRepository = FakeTripRepository(),
            itineraryRepository = FakeItineraryRepository(
                initial = listOf(dayDto(id = "day-1", dayNumber = 1, city = "Rome", lat = 41.9, lon = 12.5))
            ),
            weatherRepository = weatherRepository,
        )
        advanceUntilIdle()

        weatherRepository.refreshError = IOException("offline")
        val collected = mutableListOf<TripForecastEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(TripForecastEvent.OnUserRefresh)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            TripForecastEffect.ShowToastRes(R.string.common_error_server_unreachable),
            collected.single(),
        )
        val state = viewModel.state.value as TripForecastState.Content
        assertFalse(state.isRefreshing)
        assertTrue(weatherRepository.refreshRequests.size >= 2)
    }

    @Test
    fun given_screenOpen_when_backClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val navigator = FakeNavigator()
        val viewModel = createViewModel(
            appContext = appContext,
            navigator = navigator,
            tripRepository = FakeTripRepository(),
            itineraryRepository = FakeItineraryRepository(
                initial = listOf(dayDto(id = "day-1", dayNumber = 1, city = "Rome", lat = 41.9, lon = 12.5))
            ),
            weatherRepository = FakeWeatherRepository().apply { setResponse("Rome", weatherResponse()) },
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripForecastEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    private fun createViewModel(
        appContext: Context,
        navigator: FakeNavigator,
        tripRepository: FakeTripRepository,
        itineraryRepository: FakeItineraryRepository,
        weatherRepository: FakeWeatherRepository,
    ): TripForecastViewModel {
        val uiErrorMapper = UiErrorMapper(networkStateProvider)
        return TripForecastViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.TripForecast.ARG_TRIP_ID to "trip-1")
            ),
            appContext = appContext,
            appNavigator = navigator,
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
            weatherRepository = weatherRepository,
            apiCaller = apiCaller,
            uiErrorMapper = uiErrorMapper,
        )
    }
}
