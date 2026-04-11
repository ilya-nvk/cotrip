package nvk.cotrip.ui.idea.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.idea.FakeCommentEventsSourceFactory
import nvk.cotrip.ui.idea.FakeIdeaRepository
import nvk.cotrip.ui.idea.ideaDto
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.details.TripDetailsFakeItineraryRepository
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
import nvk.cotrip.ui.trip.details.TripDetailsFakeTripRepository
import nvk.cotrip.ui.trip.details.tripDetailsDayDto
import nvk.cotrip.ui.trip.details.tripDetailsTripDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class TripIdeasViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripId = "trip-ideas-1"
    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)

    @Test
    fun given_successfulRefresh_when_init_then_emitsContentState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(3)
        val trip = tripDetailsTripDto(id = tripId, start = start, end = end)
        val idea = ideaDto(id = "idea-1", tripId = tripId, title = "Idea One")
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
            itineraryRepository = TripDetailsFakeItineraryRepository(days = listOf(day)),
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertTrue(state is TripIdeasState.Content)
        assertEquals(1, (state as TripIdeasState.Content).ideas.size)
        assertEquals("Idea One", state.ideas.single().title)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnBackClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_onAddIdeaClick_then_navigatesToCreateIdea() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnAddIdeaClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.destinations.size)
        assertTrue(navigator.destinations.single() is Destination.CreateIdea)
        assertEquals(tripId, (navigator.destinations.single() as Destination.CreateIdea).tripId)
    }

    @Test
    fun given_screenOpen_when_onGetAiSuggestionsClick_then_navigatesToBuildRoute() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnGetAiSuggestionsClick)
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.BuildRoute })
    }

    @Test
    fun given_contentShown_when_onIdeaClick_then_navigatesToIdeaDetails() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val idea = ideaDto(id = "idea-1", tripId = tripId)
        val viewModel = createViewModel(
            navigator = navigator,
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnIdeaClick("idea-1"))
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.IdeaDetails })
        val dest = navigator.destinations.single() as Destination.IdeaDetails
        assertEquals(tripId, dest.tripId)
        assertEquals("idea-1", dest.ideaId)
    }

    @Test
    fun given_dayOptionsExist_when_onAddToItineraryClick_then_opensDayPicker() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, start = start, end = end)
        val idea = ideaDto(id = "idea-1", tripId = tripId)
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
            itineraryRepository = TripDetailsFakeItineraryRepository(days = listOf(day)),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnAddToItineraryClick("idea-1"))
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as TripIdeasState.Content
        assertNotNull(state.dayPicker)
        assertEquals("idea-1", state.dayPicker?.ideaId)
    }

    @Test
    fun given_dayPickerOpen_when_onDismissDayPicker_then_clearsDayPicker() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, start = start, end = end)
        val idea = ideaDto(id = "idea-1", tripId = tripId)
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
            itineraryRepository = TripDetailsFakeItineraryRepository(days = listOf(day)),
        )
        advanceUntilIdle()
        viewModel.onEvent(TripIdeasEvent.OnAddToItineraryClick("idea-1"))
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnDismissDayPicker)
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as TripIdeasState.Content
        assertNull(state.dayPicker)
    }

    @Test
    fun given_userRefreshFailure_when_onUserRefresh_then_emitsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val itineraryRepo = TripDetailsFakeItineraryRepository(
            days = listOf(tripDetailsDayDto(id = "day-1", dayNumber = 1, date = LocalDate.now(), city = "Rome", isOutOfRange = false)),
        )
        itineraryRepo.refreshItineraryResult = Result.failure(RuntimeException("network error"))
        val viewModel = createViewModel(itineraryRepository = itineraryRepo)
        advanceUntilIdle()

        val effects = mutableListOf<TripIdeasEffect>()
        launch { viewModel.effects.take(1).toList(effects) }
        advanceUntilIdle()
        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnUserRefresh)
        advanceUntilIdle()

        // THEN
        assertTrue(effects.any { it is TripIdeasEffect.ShowToastRes })
    }

    @Test
    fun given_dayPickerOpen_when_onDaySelectedSuccess_then_updatesIdeaUi() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, start = start, end = end)
        val idea = ideaDto(id = "idea-1", tripId = tripId)
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val itineraryRepo = TripDetailsFakeItineraryRepository(days = listOf(day))
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
            itineraryRepository = itineraryRepo,
        )
        advanceUntilIdle()
        viewModel.onEvent(TripIdeasEvent.OnAddToItineraryClick("idea-1"))
        advanceUntilIdle()

        val state = viewModel.state.value as TripIdeasState.Content
        val dayOption = state.dayPicker!!.days.single()
        // WHEN
        viewModel.onEvent(TripIdeasEvent.OnDaySelected(dayOption))
        advanceUntilIdle()

        // THEN
        val updated = viewModel.state.value as TripIdeasState.Content
        assertEquals(1, updated.ideas.single().addedDay)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(
            mapOf(Destination.TripIdeas.ARG_TRIP_ID to tripId)
        ),
        navigator: TripDetailsFakeNavigator = TripDetailsFakeNavigator(),
        tripRepository: TripDetailsFakeTripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = tripId,
                start = LocalDate.now(),
                end = LocalDate.now().plusDays(1),
            ),
        ),
        ideaRepository: FakeIdeaRepository = FakeIdeaRepository(
            initialIdea = ideaDto(id = "idea-1", tripId = tripId),
        ),
        itineraryRepository: TripDetailsFakeItineraryRepository = TripDetailsFakeItineraryRepository(
            days = listOf(
                tripDetailsDayDto(
                    id = "day-1",
                    dayNumber = 1,
                    date = LocalDate.now(),
                    city = "Rome",
                    isOutOfRange = false,
                ),
            ),
        ),
        sessionStore: SessionStore = mockk<SessionStore>().also {
            every { it.getAccessToken() } returns ""
        },
        commentEventsSourceFactory: FakeCommentEventsSourceFactory = FakeCommentEventsSourceFactory(),
    ): TripIdeasViewModel = TripIdeasViewModel(
        savedStateHandle = savedStateHandle,
        appNavigator = navigator,
        tripRepository = tripRepository,
        ideaRepository = ideaRepository,
        itineraryRepository = itineraryRepository,
        sessionStore = sessionStore,
        commentEventsSourceFactory = commentEventsSourceFactory,
        apiCaller = apiCaller,
        uiErrorMapper = uiErrorMapper,
    )
}
