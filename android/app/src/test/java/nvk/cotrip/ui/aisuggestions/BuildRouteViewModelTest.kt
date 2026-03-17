package nvk.cotrip.ui.aisuggestions

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.details.TripDetailsFakeItineraryRepository
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
import nvk.cotrip.ui.trip.details.tripDetailsDayDto
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
class BuildRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    @Test
    fun given_emptyItinerary_when_init_then_loadsStateWithTripIdAndOptions() = runTest {
        // GIVEN
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        assertEquals("trip-1", viewModel.state.value.tripId)
        assertNull(viewModel.state.value.city)
        assertEquals("", viewModel.state.value.description)
        assertNotNull(viewModel.state.value.typeOptions)
        assertNotNull(viewModel.state.value.timeOfDayOptions)
        assertNotNull(viewModel.state.value.budgetOptions)
        assertNull(viewModel.state.value.cityPicker)
    }

    @Test
    fun given_itineraryWithCities_when_onCityClick_then_pickerShowsThoseCities() = runTest {
        // GIVEN
        val itineraryRepository = TripDetailsFakeItineraryRepository(
            days = listOf(
                tripDetailsDayDto(
                    id = "day-1",
                    dayNumber = 1,
                    date = LocalDate.now().plusDays(1),
                    city = "Rome",
                ),
                tripDetailsDayDto(
                    id = "day-2",
                    dayNumber = 2,
                    date = LocalDate.now().plusDays(2),
                    city = "Florence",
                ),
            ),
        )
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            itineraryRepository = itineraryRepository,
            tripId = "trip-1",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnCityClick)
        advanceUntilIdle()

        // THEN
        val picker = viewModel.state.value.cityPicker
        assertNotNull(picker)
        assertTrue(picker!!.cities.contains("Rome"))
        assertTrue(picker.cities.contains("Florence"))
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_onCityClick_then_showsCityPicker() = runTest {
        // GIVEN
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnCityClick)
        advanceUntilIdle()

        // THEN
        assertNotNull(viewModel.state.value.cityPicker)
    }

    @Test
    fun given_cityPickerOpen_when_onDismissCityPicker_then_hidesCityPicker() = runTest {
        // GIVEN
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )
        advanceUntilIdle()
        viewModel.onEvent(BuildRouteEvent.OnCityClick)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.cityPicker)

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnDismissCityPicker)
        advanceUntilIdle()

        // THEN
        assertNull(viewModel.state.value.cityPicker)
    }

    @Test
    fun given_cityPickerOpen_when_onCitySelected_then_setsCityAndClosesPicker() = runTest {
        // GIVEN
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )
        advanceUntilIdle()
        viewModel.onEvent(BuildRouteEvent.OnCityClick)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnCitySelected("Paris"))
        advanceUntilIdle()

        // THEN
        assertEquals("Paris", viewModel.state.value.city)
        assertNull(viewModel.state.value.cityPicker)
    }

    @Test
    fun given_screenOpen_when_onDescriptionChange_then_updatesDescription() = runTest {
        // GIVEN
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = TripDetailsFakeNavigator(),
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnDescriptionChange("cultural walk"))

        // THEN
        assertEquals("cultural walk", viewModel.state.value.description)
    }

    @Test
    fun given_cityAndDescriptionSet_when_onGenerateClick_then_navigatesToRouteSuggestions() = runTest {
        // GIVEN
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )
        advanceUntilIdle()
        viewModel.onEvent(BuildRouteEvent.OnCitySelected("Rome"))
        viewModel.onEvent(BuildRouteEvent.OnDescriptionChange("museums"))
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnGenerateClick)
        advanceUntilIdle()

        // THEN
        val routeSuggestions = navigator.destinations.filterIsInstance<Destination.RouteSuggestions>()
        assertEquals(1, routeSuggestions.size)
        assertEquals("trip-1", routeSuggestions.single().tripId)
        assertEquals("Rome", routeSuggestions.single().city)
        assertEquals("museums", routeSuggestions.single().description)
    }

    @Test
    fun given_noCitySelected_when_onGenerateClick_then_doesNotNavigate() = runTest {
        // GIVEN
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            itineraryRepository = TripDetailsFakeItineraryRepository(emptyList()),
            tripId = "trip-1",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(BuildRouteEvent.OnGenerateClick)
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.isEmpty())
    }

    private fun createViewModel(
        appContext: android.content.Context,
        navigator: TripDetailsFakeNavigator,
        itineraryRepository: TripDetailsFakeItineraryRepository,
        tripId: String,
    ): BuildRouteViewModel = BuildRouteViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Destination.BuildRoute.ARG_TRIP_ID to tripId)),
        appContext = appContext,
        appNavigator = navigator,
        itineraryRepository = itineraryRepository,
    )
}
