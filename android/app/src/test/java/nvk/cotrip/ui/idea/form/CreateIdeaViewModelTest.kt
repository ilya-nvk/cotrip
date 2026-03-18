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
class CreateIdeaViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_tripExists_when_init_then_loadsTripMetaAndFillsCurrencySymbol() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val tripRepository = ActivityFakeTripRepository(trip = trip)
        val viewModel = createViewModel(tripRepository = tripRepository)

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertEquals(IdeaFormMode.Create, state.mode)
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
    fun given_emptyTitle_when_onPrimaryClick_then_doesNotCallCreateIdea() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val ideaRepository = FakeIdeaRepository()
        val viewModel = createViewModel(ideaRepository = ideaRepository)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaFormEvent.OnPrimaryClick)
        advanceUntilIdle()

        // THEN
        assertEquals(0, ideaRepository.createIdeaCalls.size)
    }

    @Test
    fun given_validData_when_onPrimaryClick_then_createsIdeaAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val trip = activityTripDto(id = "trip-1")
        val navigator = ActivityFakeNavigator()
        val ideaRepository = FakeIdeaRepository()
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = ActivityFakeTripRepository(trip = trip),
            ideaRepository = ideaRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaFormEvent.OnTitleChange("Museum visit"))
        viewModel.onEvent(IdeaFormEvent.OnPrimaryClick)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, ideaRepository.createIdeaCalls.size)
        assertEquals("trip-1", ideaRepository.createIdeaCalls.single().first)
        assertEquals("Museum visit", ideaRepository.createIdeaCalls.single().second.title)
        assertEquals(1, navigator.popCalls)
    }

    private fun createViewModel(
        navigator: ActivityFakeNavigator = ActivityFakeNavigator(),
        tripRepository: ActivityFakeTripRepository = ActivityFakeTripRepository(
            trip = activityTripDto(id = "trip-1"),
        ),
        itineraryRepository: ActivityFakeItineraryRepository = ActivityFakeItineraryRepository(),
        ideaRepository: FakeIdeaRepository = FakeIdeaRepository(),
    ): CreateIdeaViewModel = CreateIdeaViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(Destination.CreateIdea.ARG_TRIP_ID to "trip-1")
        ),
        appNavigator = navigator,
        tripRepository = tripRepository,
        itineraryRepository = itineraryRepository,
        ideaRepository = ideaRepository,
        apiCaller = apiCaller,
        uiErrorMapper = UiErrorMapper(networkStateProvider),
    )
}
