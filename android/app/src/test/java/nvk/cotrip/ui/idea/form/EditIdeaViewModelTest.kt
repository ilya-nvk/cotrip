package nvk.cotrip.ui.idea.form

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
import nvk.cotrip.ui.activity.activityTripDto
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.idea.FakeIdeaRepository
import nvk.cotrip.ui.idea.ideaDto
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditIdeaViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_ideaExists_when_init_then_loadsIdeaAndFillsState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val idea = ideaDto(
            id = "idea-1",
            tripId = "trip-1",
            title = "Louvre",
            city = "Paris",
            notes = "Book in advance",
        )
        val ideaRepository = FakeIdeaRepository(initialIdea = idea)
        val tripRepository = ActivityFakeTripRepository(trip = activityTripDto(id = "trip-1"))
        tripRepository.setTrips(listOf(activityTripDto(id = "trip-1")))
        val viewModel = createViewModel(
            tripRepository = tripRepository,
            ideaRepository = ideaRepository,
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertEquals(IdeaFormMode.Edit, state.mode)
        assertEquals("idea-1", state.ideaId)
        assertEquals("Louvre", state.title)
        assertEquals("Paris", state.city)
        assertEquals("Book in advance", state.notes)
        assertTrue(state.currencySymbol.isNotBlank())
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ActivityFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaFormEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_validData_when_onPrimaryClick_then_updatesIdeaAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val idea = ideaDto(id = "idea-1", tripId = "trip-1", title = "Original")
        val navigator = ActivityFakeNavigator()
        val ideaRepository = FakeIdeaRepository(initialIdea = idea)
        val tripRepository = ActivityFakeTripRepository(trip = activityTripDto(id = "trip-1"))
        tripRepository.setTrips(listOf(activityTripDto(id = "trip-1")))
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = tripRepository,
            ideaRepository = ideaRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaFormEvent.OnTitleChange("Updated title"))
        viewModel.onEvent(IdeaFormEvent.OnPrimaryClick)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, ideaRepository.updateIdeaCalls.size)
        assertEquals("idea-1", ideaRepository.updateIdeaCalls.single().first)
        assertEquals("Updated title", ideaRepository.updateIdeaCalls.single().second.title)
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_ideaLoaded_when_onDeleteClick_then_deletesIdeaAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val idea = ideaDto(id = "idea-1", tripId = "trip-1", title = "To delete")
        val navigator = ActivityFakeNavigator()
        val ideaRepository = FakeIdeaRepository(initialIdea = idea)
        val tripRepository = ActivityFakeTripRepository(trip = activityTripDto(id = "trip-1"))
        tripRepository.setTrips(listOf(activityTripDto(id = "trip-1")))
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = tripRepository,
            ideaRepository = ideaRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaFormEvent.OnDeleteClick)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, ideaRepository.deleteIdeaCalls.size)
        assertEquals("idea-1", ideaRepository.deleteIdeaCalls.single())
        assertEquals(1, navigator.popCalls)
    }

    private fun createViewModel(
        navigator: ActivityFakeNavigator = ActivityFakeNavigator(),
        tripRepository: ActivityFakeTripRepository = ActivityFakeTripRepository(
            trip = activityTripDto(id = "trip-1"),
        ).apply { setTrips(listOf(activityTripDto(id = "trip-1"))) },
        itineraryRepository: ActivityFakeItineraryRepository = ActivityFakeItineraryRepository(),
        ideaRepository: FakeIdeaRepository = FakeIdeaRepository(
            initialIdea = ideaDto(id = "idea-1", tripId = "trip-1", title = "Test"),
        ),
    ): EditIdeaViewModel = EditIdeaViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                Destination.EditIdea.ARG_TRIP_ID to "trip-1",
                Destination.EditIdea.ARG_IDEA_ID to "idea-1",
            )
        ),
        appNavigator = navigator,
        tripRepository = tripRepository,
        itineraryRepository = itineraryRepository,
        ideaRepository = ideaRepository,
        apiCaller = apiCaller,
        uiErrorMapper = UiErrorMapper(networkStateProvider),
    )
}
