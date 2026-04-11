package nvk.cotrip.ui.idea.details

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.notifications.SystemNotificationManager
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.idea.FakeCommentEventsSourceFactory
import nvk.cotrip.ui.idea.FakeIdeaRepository
import nvk.cotrip.ui.idea.ideaDto
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.details.TripDetailsFakeItineraryRepository
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
import nvk.cotrip.ui.trip.details.TripDetailsFakeTripRepository
import nvk.cotrip.ui.trip.details.TripDetailsFakeUserRepository
import nvk.cotrip.ui.trip.details.tripDetailsDayDto
import nvk.cotrip.ui.trip.details.tripDetailsMemberDto
import nvk.cotrip.ui.idea.common.IdeaDayOptionUi
import nvk.cotrip.ui.trip.details.tripDetailsTripDto
import nvk.cotrip.ui.trip.details.tripDetailsUserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class IdeaDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripId = "trip-details-1"
    private val ideaId = "idea-details-1"
    private val ownerId = "owner-1"
    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)

    @Test
    fun given_successfulLoad_when_init_then_fillsState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, ownerId = ownerId, start = start, end = end)
        val idea = ideaDto(id = ideaId, tripId = tripId, title = "My Idea", city = "Rome", status = "suggested")
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip, members = listOf(tripDetailsMemberDto("user-1", "U1"))),
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId)),
            itineraryRepository = TripDetailsFakeItineraryRepository(days = listOf(day)),
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertFalse(state.title.isEmpty())
        assertEquals("My Idea", state.title)
        assertEquals("Rome", state.city)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnBackClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_onEditClick_then_navigatesToEditIdea() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnEditClick)
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.EditIdea })
        val dest = navigator.destinations.single() as Destination.EditIdea
        assertEquals(tripId, dest.tripId)
        assertEquals(ideaId, dest.ideaId)
    }

    @Test
    fun given_dayOptionsExist_when_onAddToItineraryClick_then_opensDayPicker() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, ownerId = ownerId, start = start, end = end)
        val idea = ideaDto(id = ideaId, tripId = tripId)
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId)),
            itineraryRepository = TripDetailsFakeItineraryRepository(days = listOf(day)),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnAddToItineraryClick)
        advanceUntilIdle()

        // THEN
        assertNotNull(viewModel.state.value.dayPicker)
    }

    @Test
    fun given_dayPickerOpen_when_onDismissDayPicker_then_clearsDayPicker() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, ownerId = ownerId, start = start, end = end)
        val idea = ideaDto(id = ideaId, tripId = tripId)
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId)),
            itineraryRepository = TripDetailsFakeItineraryRepository(days = listOf(day)),
        )
        advanceUntilIdle()
        viewModel.onEvent(IdeaDetailsEvent.OnAddToItineraryClick)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnDismissDayPicker)
        advanceUntilIdle()

        // THEN
        assertNull(viewModel.state.value.dayPicker)
    }

    @Test
    fun given_ideaLoaded_when_onDeleteClickSuccess_then_navigatesBack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = TripDetailsFakeNavigator()
        val idea = ideaDto(id = ideaId, tripId = tripId)
        val viewModel = createViewModel(
            navigator = navigator,
            ideaRepository = FakeIdeaRepository(initialIdea = idea),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnDeleteClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_ideaLoaded_when_onApproveClickSuccess_then_updatesStatus() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val idea = ideaDto(id = ideaId, tripId = tripId, status = "suggested")
        val ideaRepo = FakeIdeaRepository(initialIdea = idea)
        val viewModel = createViewModel(ideaRepository = ideaRepo)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnApproveClick)
        advanceUntilIdle()

        // THEN
        assertEquals("approved", viewModel.state.value.status)
    }

    @Test
    fun given_ideaLoaded_when_onRejectClickSuccess_then_updatesStatus() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val idea = ideaDto(id = ideaId, tripId = tripId, status = "suggested")
        val viewModel = createViewModel(ideaRepository = FakeIdeaRepository(initialIdea = idea))
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnRejectClick)
        advanceUntilIdle()

        // THEN
        assertEquals("rejected", viewModel.state.value.status)
    }

    @Test
    fun given_dayPickerOpen_when_onDaySelected_then_convertsIdeaToActivity() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val start = LocalDate.now()
        val end = LocalDate.now().plusDays(2)
        val trip = tripDetailsTripDto(id = tripId, ownerId = ownerId, start = start, end = end)
        val idea = ideaDto(id = ideaId, tripId = tripId)
        val day = tripDetailsDayDto(id = "day-1", dayNumber = 1, date = start, city = "Rome", isOutOfRange = false)
        val ideaRepo = FakeIdeaRepository(initialIdea = idea)
        val viewModel = createViewModel(
            tripRepository = TripDetailsFakeTripRepository(trip = trip),
            ideaRepository = ideaRepo,
            userRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId)),
            itineraryRepository = TripDetailsFakeItineraryRepository(days = listOf(day)),
        )
        advanceUntilIdle()
        viewModel.onEvent(IdeaDetailsEvent.OnAddToItineraryClick)
        advanceUntilIdle()
        val dayOption = viewModel.state.value.dayPicker!!.days.single()

        // WHEN
        viewModel.onEvent(IdeaDetailsEvent.OnDaySelected(dayOption))
        advanceUntilIdle()

        // THEN
        assertEquals(1, ideaRepo.convertIdeaToActivityCalls.size)
        assertEquals(ideaId, ideaRepo.convertIdeaToActivityCalls.single().first)
        assertEquals("day-1", ideaRepo.convertIdeaToActivityCalls.single().second.dayId)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(
            mapOf(
                Destination.IdeaDetails.ARG_TRIP_ID to tripId,
                Destination.IdeaDetails.ARG_IDEA_ID to ideaId,
            )
        ),
        navigator: TripDetailsFakeNavigator = TripDetailsFakeNavigator(),
        tripRepository: TripDetailsFakeTripRepository = TripDetailsFakeTripRepository(
            trip = tripDetailsTripDto(
                id = tripId,
                ownerId = ownerId,
                start = LocalDate.now(),
                end = LocalDate.now().plusDays(1),
            ),
            members = emptyList(),
        ),
        ideaRepository: FakeIdeaRepository = FakeIdeaRepository(
            initialIdea = ideaDto(id = ideaId, tripId = tripId),
        ),
        itineraryRepository: TripDetailsFakeItineraryRepository = TripDetailsFakeItineraryRepository(
            days = listOf(
                tripDetailsDayDto(id = "day-1", dayNumber = 1, date = LocalDate.now(), city = "Rome", isOutOfRange = false),
            ),
        ),
        userRepository: TripDetailsFakeUserRepository = TripDetailsFakeUserRepository(me = tripDetailsUserDto(id = ownerId)),
        notificationRepository: NotificationRepository = mockk<NotificationRepository>().also {
            every { it.notifications } returns flowOf(emptyList())
            every { it.settings } returns flowOf(emptyList())
            coEvery { it.refreshNotifications() } returns Result.success(Unit)
            coEvery { it.markRead(any()) } returns Unit
            coEvery { it.markReadBulkNonComment() } returns Result.success(0)
            coEvery { it.markReadBulkIdeaComments(any()) } returns Result.success(0)
            coEvery { it.refreshSettings() } returns Result.success(Unit)
            coEvery { it.updateSettings(any()) } returns Result.success(Unit)
            coEvery { it.upsertPushToken(any(), any()) } returns Result.success(Unit)
            coEvery { it.deletePushToken(any()) } returns Result.success(Unit)
        },
        systemNotificationManager: SystemNotificationManager = mockk(relaxed = true),
        sessionStore: SessionStore = mockk<SessionStore>().also {
            every { it.getAccessToken() } returns ""
        },
        commentEventsSourceFactory: FakeCommentEventsSourceFactory = FakeCommentEventsSourceFactory(),
    ): IdeaDetailsViewModel {
        val appContext = ApplicationProvider.getApplicationContext<Application>()
        return IdeaDetailsViewModel(
            savedStateHandle = savedStateHandle,
            appContext = appContext,
            appNavigator = navigator,
            tripRepository = tripRepository,
            ideaRepository = ideaRepository,
            itineraryRepository = itineraryRepository,
            userRepository = userRepository,
            notificationRepository = notificationRepository,
            systemNotificationManager = systemNotificationManager,
            sessionStore = sessionStore,
            commentEventsSourceFactory = commentEventsSourceFactory,
            json = Json { ignoreUnknownKeys = true },
            apiCaller = apiCaller,
            uiErrorMapper = uiErrorMapper,
        )
    }
}
