package nvk.cotrip.ui.trip.details

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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_futureOwnerTrip_when_init_then_buildsContentStateWithWeatherAndOverview() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val trip = tripDetailsTripDto(
            id = "trip-1",
            ownerId = "owner-1",
            start = today.plusDays(1),
            end = today.plusDays(4),
        )
        val tripRepository = TripDetailsFakeTripRepository(
            trip = trip,
            members = listOf(
                tripDetailsMemberDto(id = "owner-1", initials = "OW"),
                tripDetailsMemberDto(id = "guest-1", initials = "GT"),
            ),
        )
        val itineraryRepository = TripDetailsFakeItineraryRepository(
            days = listOf(
                tripDetailsDayDto(
                    id = "day-1",
                    dayNumber = 1,
                    date = today.plusDays(1),
                    city = "Rome",
                    activities = listOf(
                        tripDetailsActivityDto(
                            id = "act-2",
                            dayId = "day-1",
                            title = "Museum",
                            order = 2,
                            timeText = "11:30",
                        ),
                        tripDetailsActivityDto(
                            id = "act-0",
                            dayId = "day-1",
                            title = "Breakfast",
                            order = 0,
                            timeText = "09:00",
                        ),
                        tripDetailsActivityDto(
                            id = "act-1",
                            dayId = "day-1",
                            title = "Walk",
                            order = 1,
                        ),
                    ),
                )
            )
        )
        val weatherRepository = TripDetailsFakeWeatherRepository().apply {
            setCachedResponse(
                city = "Rome",
                response = tripDetailsWeatherResponse(
                    city = "Rome",
                    date = today.plusDays(1),
                ),
            )
        }
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            ideaRepository = TripDetailsFakeIdeaRepository(
                ideas = listOf(
                    tripDetailsIdeaDto(
                        id = "idea-1",
                        tripId = "trip-1",
                        title = "Find rooftop cafe",
                    )
                )
            ),
            expenseRepository = TripDetailsFakeExpenseRepository(
                expenses = listOf(
                    tripDetailsExpenseDto(
                        id = "expense-1",
                        tripId = "trip-1",
                        amount = 35.50,
                    )
                )
            ),
            itineraryRepository = itineraryRepository,
            weatherRepository = weatherRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as TripDetailsState.Content
        assertTrue(state.isOwner)
        assertFalse(state.isPast)
        assertFalse(state.isEmpty)
        assertEquals("Spring Rome", state.header.title)
        assertEquals(2, state.travelers.size)
        assertTrue(state.peopleCountText.contains("2"))
        assertEquals("Rome", state.weather.city)
        assertTrue(state.weather.days.isNotEmpty())
        assertEquals(1, state.overview.ideasCount)
        assertTrue(state.overview.expensesAmount.startsWith("€35"))
        assertEquals("Breakfast", state.nextInTrip.lines.first().title)
        assertTrue(weatherRepository.cachedRequests.isNotEmpty())
    }

    @Test
    fun given_futureOwner_when_eventsFired_then_navigateToExpectedDestinations() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "owner-1",
                start = today.plusDays(1),
                end = today.plusDays(2),
            )
        )
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            ideaRepository = TripDetailsFakeIdeaRepository(ideas = listOf(tripDetailsIdeaDto(id = "idea-1"))),
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
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripDetailsEvent.OnEditClick)
        viewModel.onEvent(TripDetailsEvent.OnInviteTravelersClick)
        viewModel.onEvent(TripDetailsEvent.OnMembersClick)
        viewModel.onEvent(TripDetailsEvent.OnViewForecastClick)
        viewModel.onEvent(TripDetailsEvent.OnViewItineraryClick)
        viewModel.onEvent(TripDetailsEvent.OnIdeasClick)
        viewModel.onEvent(TripDetailsEvent.OnExpensesClick)
        viewModel.onEvent(TripDetailsEvent.OnPrimaryCtaClick)
        viewModel.onEvent(TripDetailsEvent.OnBackClick)

        // THEN
        assertEquals(
            listOf(
                Destination.EditTrip("trip-1"),
                Destination.InviteTravelers("trip-1"),
                Destination.TripMembers("trip-1"),
                Destination.TripForecast("trip-1"),
                Destination.TripItinerary("trip-1"),
                Destination.TripIdeas("trip-1"),
                Destination.Expenses("trip-1"),
                Destination.BuildRoute("trip-1"),
            ),
            navigator.destinations,
        )
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_pastTrip_when_restrictedEventsFired_then_doNotNavigate() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "owner-1",
                start = today.minusDays(5),
                end = today.minusDays(1),
            )
        )
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            ideaRepository = TripDetailsFakeIdeaRepository(),
            expenseRepository = TripDetailsFakeExpenseRepository(),
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
            weatherRepository = TripDetailsFakeWeatherRepository(),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripDetailsEvent.OnEditClick)
        viewModel.onEvent(TripDetailsEvent.OnInviteTravelersClick)
        viewModel.onEvent(TripDetailsEvent.OnBrowseIdeasClick)
        viewModel.onEvent(TripDetailsEvent.OnIdeasClick)
        viewModel.onEvent(TripDetailsEvent.OnWeatherCityClick)
        viewModel.onEvent(TripDetailsEvent.OnViewForecastClick)
        viewModel.onEvent(TripDetailsEvent.OnPrimaryCtaClick)

        // THEN
        assertTrue(navigator.destinations.isEmpty())
    }

    @Test
    fun given_getTripFails_when_userRefresh_then_emitsMappedToastAndResetsRefreshing() = runTest {
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val tripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = "trip-1",
                ownerId = "owner-1",
                start = today.plusDays(1),
                end = today.plusDays(3),
            )
        )
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            tripRepository = tripRepository,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            ideaRepository = TripDetailsFakeIdeaRepository(),
            expenseRepository = TripDetailsFakeExpenseRepository(),
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
            weatherRepository = TripDetailsFakeWeatherRepository(),
        )
        advanceUntilIdle()
        tripRepository.getTripError = IOException("offline")
        val effects = mutableListOf<TripDetailsEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }

        // WHEN
        viewModel.onEvent(TripDetailsEvent.OnUserRefresh)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            TripDetailsEffect.ShowToastRes(R.string.common_error_server_unreachable),
            effects.single(),
        )
        val state = viewModel.state.value as TripDetailsState.Content
        assertFalse(state.isRefreshing)
    }

    @Test
    fun given_pastTrip_when_init_then_skipsWeatherLoadingRequests() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val today = LocalDate.now()
        val weatherRepository = TripDetailsFakeWeatherRepository().apply {
            setCachedResponse(
                city = "Rome",
                response = tripDetailsWeatherResponse(city = "Rome", date = today.minusDays(3)),
            )
        }
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            tripRepository = TripDetailsFakeTripRepository(
                trip = tripDetailsTripDto(
                    id = "trip-1",
                    ownerId = "owner-1",
                    start = today.minusDays(5),
                    end = today.minusDays(1),
                )
            ),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = "owner-1")),
            ideaRepository = TripDetailsFakeIdeaRepository(),
            expenseRepository = TripDetailsFakeExpenseRepository(),
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
            weatherRepository = weatherRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as TripDetailsState.Content
        assertTrue(state.isPast)
        assertTrue(state.weather.days.isEmpty())
        assertTrue(weatherRepository.cachedRequests.isEmpty())
        assertTrue(weatherRepository.refreshRequests.isEmpty())
        assertTrue(weatherRepository.snapshotRequests.isEmpty())
        assertTrue(weatherRepository.weatherRequests.isEmpty())
    }

    private fun createViewModel(
        appContext: Context,
        navigator: TripDetailsFakeNavigator,
        tripRepository: TripDetailsFakeTripRepository,
        userRepository: TripDetailsFakeUserRepository,
        ideaRepository: TripDetailsFakeIdeaRepository,
        expenseRepository: TripDetailsFakeExpenseRepository,
        itineraryRepository: TripDetailsFakeItineraryRepository,
        weatherRepository: TripDetailsFakeWeatherRepository,
    ): TripDetailsViewModel {
        return TripDetailsViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.TripDetails.ARG_TRIP_ID to "trip-1")
            ),
            appContext = appContext,
            appNavigator = navigator,
            tripRepository = tripRepository,
            userRepository = userRepository,
            ideaRepository = ideaRepository,
            expenseRepository = expenseRepository,
            itineraryRepository = itineraryRepository,
            weatherRepository = weatherRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
