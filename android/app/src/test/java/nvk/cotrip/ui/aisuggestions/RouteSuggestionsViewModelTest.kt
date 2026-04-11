package nvk.cotrip.ui.aisuggestions

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class RouteSuggestionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_suggestionsLoaded_when_init_then_showsContentWithSuggestions() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val suggestions = listOf(
            aiSuggestionDto(id = "s1", title = "Colosseum"),
            aiSuggestionDto(id = "s2", title = "Forum"),
        )
        val aiRepository = FakeAiSuggestionsRepository(suggestions = suggestions)
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            aiSuggestionsRepository = aiRepository,
            tripId = "trip-1",
            city = "Rome",
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val content = viewModel.state.value as? RouteSuggestionsState.Content
        assertNotNull(content)
        assertEquals("trip-1", content!!.tripId)
        assertEquals("Rome", content.city)
        assertEquals(2, content.suggestions.size)
        assertEquals("Colosseum", content.suggestions[0].title)
        assertEquals("Forum", content.suggestions[1].title)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            aiSuggestionsRepository = FakeAiSuggestionsRepository(
                suggestions = listOf(aiSuggestionDto(id = "s1", title = "A")),
            ),
            tripId = "trip-1",
            city = "Rome",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(RouteSuggestionsEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_contentShown_when_onRefreshClick_then_regeneratesSuggestions() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val suggestions = listOf(aiSuggestionDto(id = "s1", title = "First"))
        val aiRepository = FakeAiSuggestionsRepository(suggestions = suggestions)
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            aiSuggestionsRepository = aiRepository,
            tripId = "trip-1",
            city = "Rome",
        )
        advanceUntilIdle()
        assertEquals(1, aiRepository.generateSuggestionsCalls.size)

        // WHEN
        viewModel.onEvent(RouteSuggestionsEvent.OnRefreshClick)
        advanceUntilIdle()

        // THEN
        assertEquals(2, aiRepository.generateSuggestionsCalls.size)
    }

    @Test
    fun given_unsavedSuggestion_when_onSaveClickSuccess_then_updatesState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val aiRepository = FakeAiSuggestionsRepository(
            suggestions = listOf(aiSuggestionDto(id = "s1", title = "A", isSaved = false)),
        )
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            aiSuggestionsRepository = aiRepository,
            tripId = "trip-1",
            city = "Rome",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(RouteSuggestionsEvent.OnSaveClick("s1"))
        advanceUntilIdle()

        // THEN
        assertEquals(1, aiRepository.saveSuggestionToIdeasCalls.size)
        assertEquals("s1", aiRepository.saveSuggestionToIdeasCalls.single())
        val content = viewModel.state.value as RouteSuggestionsState.Content
        assertTrue(content.suggestions.single { it.id == "s1" }.isSaved)
    }

    @Test
    fun given_initFails_when_loadCompletes_then_staysLoadingAndEmitsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val aiRepository = FakeAiSuggestionsRepository(suggestions = emptyList()).apply {
            generateSuggestionsError = IOException("api down")
        }
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            aiSuggestionsRepository = aiRepository,
            tripId = "trip-1",
            city = "Rome",
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        assertTrue(viewModel.state.value is RouteSuggestionsState.Loading)
    }

    @Test
    fun given_emptySuggestions_when_loadCompletes_then_showsContentWithEmptyList() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val aiRepository = FakeAiSuggestionsRepository(suggestions = emptyList())
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            aiSuggestionsRepository = aiRepository,
            tripId = "trip-1",
            city = "Rome",
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val content = viewModel.state.value as? RouteSuggestionsState.Content
        assertNotNull(content)
        assertEquals("trip-1", content!!.tripId)
        assertEquals("Rome", content.city)
        assertTrue(content.suggestions.isEmpty())
    }

    private fun createViewModel(
        appContext: android.content.Context,
        navigator: TripDetailsFakeNavigator,
        aiSuggestionsRepository: FakeAiSuggestionsRepository,
        tripId: String,
        city: String,
        description: String? = null,
        typeOptions: List<String> = emptyList(),
        timeOfDayOptions: List<String> = emptyList(),
        budgetOptions: List<String> = emptyList(),
    ): RouteSuggestionsViewModel {
        val args = buildMap<String, Any?> {
            put(Destination.RouteSuggestions.ARG_TRIP_ID, tripId)
            put(Destination.RouteSuggestions.ARG_CITY, city)
            description?.let { put(Destination.RouteSuggestions.ARG_DESCRIPTION, it) }
            if (typeOptions.isNotEmpty()) put(Destination.RouteSuggestions.ARG_TYPE_OPTIONS, typeOptions.joinToString(","))
            if (timeOfDayOptions.isNotEmpty()) put(Destination.RouteSuggestions.ARG_TIME_OF_DAY_OPTIONS, timeOfDayOptions.joinToString(","))
            if (budgetOptions.isNotEmpty()) put(Destination.RouteSuggestions.ARG_BUDGET_OPTIONS, budgetOptions.joinToString(","))
        }
        return RouteSuggestionsViewModel(
            savedStateHandle = SavedStateHandle(args.mapValues { it.value?.toString() }),
            appContext = appContext,
            appNavigator = navigator,
            aiSuggestionsRepository = aiSuggestionsRepository,
            apiCaller = apiCaller,
            uiErrorMapper = uiErrorMapper,
        )
    }
}
