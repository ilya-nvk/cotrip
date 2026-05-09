package nvk.cotrip.ui.outofrangedays

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OutOfRangeDaysViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_outOfRangeDaysExist_when_init_then_loadsAndFormatsState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val itineraryRepository = FakeItineraryRepository(
            days = listOf(
                day(
                    id = "day-3",
                    dayNumber = 3,
                    date = "2026-01-13",
                    city = "Rome",
                    isOutOfRange = true,
                    activities = listOf(
                        activity("Breakfast", 0),
                        activity("Museum", 1),
                        activity("Dinner", 2),
                    ),
                )
            )
        )

        val viewModel = createViewModel(
            navigator = FakeNavigator(),
            tripRepository = FakeTripRepository(),
            itineraryRepository = itineraryRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value as OutOfRangeDaysState.Content
        assertEquals("trip-1", state.tripId)
        assertEquals(
            formatRange(LocalDate.parse("2026-01-10"), LocalDate.parse("2026-01-12")),
            state.dateRangeText,
        )
        assertEquals(formatDate(LocalDate.parse("2026-01-13")), state.proposedEndDateText)
        assertEquals(1, state.days.size)
        assertEquals(listOf("Breakfast", "Museum"), state.days.first().activitiesPreview)
        assertEquals(1, state.days.first().hiddenActivitiesCount)
        assertEquals(listOf("trip-1"), itineraryRepository.refreshCalls)
    }

    @Test
    fun given_contentShown_when_extendEndClick_then_trimsAndPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val itineraryRepository = FakeItineraryRepository(
            days = listOf(
                day(id = "day-3", dayNumber = 3, date = "2026-01-13", city = "Rome", isOutOfRange = true)
            )
        )
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = FakeTripRepository(),
            itineraryRepository = itineraryRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(OutOfRangeDaysEvent.OnExtendEndClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, itineraryRepository.trimCalls.size)
        assertEquals("extend_end", itineraryRepository.trimCalls.single().action)
        assertEquals(listOf("day-3"), itineraryRepository.trimCalls.single().dayIds)
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_noOutOfRangeDays_when_removeClick_then_onlyPops() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val itineraryRepository = FakeItineraryRepository(days = emptyList())
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = FakeTripRepository(),
            itineraryRepository = itineraryRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(OutOfRangeDaysEvent.OnRemoveClick)
        advanceUntilIdle()

        // THEN
        assertTrue(itineraryRepository.trimCalls.isEmpty())
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_trimFailure_when_removeClick_then_emitsMappedErrorWithoutNavigation() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val itineraryRepository = FakeItineraryRepository(
            days = listOf(
                day(id = "day-3", dayNumber = 3, date = "2026-01-13", city = "Rome", isOutOfRange = true)
            )
        ).apply {
            trimError = IOException("network")
        }
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = FakeTripRepository(),
            itineraryRepository = itineraryRepository,
        )
        advanceUntilIdle()

        val collected = mutableListOf<OutOfRangeDaysEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(OutOfRangeDaysEvent.OnRemoveClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            OutOfRangeDaysEffect.ShowToastRes(R.string.common_error_server_unreachable),
            collected.single(),
        )
        assertEquals(0, navigator.popCalls)
    }

    @Test
    fun given_loadFailure_when_initCompletes_then_popsBack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val itineraryRepository = FakeItineraryRepository(
            days = listOf(day(id = "day-1", dayNumber = 1, date = "2026-01-10", city = "Rome", isOutOfRange = false))
        ).apply {
            refreshResult = Result.failure(IOException("offline"))
        }

        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = FakeTripRepository(),
            itineraryRepository = itineraryRepository,
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_backClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val viewModel = createViewModel(
            navigator = navigator,
            tripRepository = FakeTripRepository(),
            itineraryRepository = FakeItineraryRepository(
                days = listOf(day(id = "day-3", dayNumber = 3, date = "2026-01-13", city = "Rome", isOutOfRange = true))
            ),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(OutOfRangeDaysEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    private fun createViewModel(
        navigator: FakeNavigator,
        tripRepository: FakeTripRepository,
        itineraryRepository: FakeItineraryRepository,
    ): OutOfRangeDaysViewModel {
        return OutOfRangeDaysViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.OutOfRangeDays.ARG_TRIP_ID to "trip-1")
            ),
            appNavigator = navigator,
            tripRepository = tripRepository,
            itineraryRepository = itineraryRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }

    private fun formatRange(start: LocalDate, end: LocalDate): String {
        val locale = Locale.getDefault()
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
        return "${start.format(formatter)} – ${end.format(formatter)}"
    }

    private fun formatDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }

    private class FakeNavigator : AppNavigator {
        var popCalls: Int = 0

        override fun navigate(destination: Destination, navOptions: (androidx.navigation.NavOptionsBuilder.() -> Unit)?) = Unit

        override fun popBackStack(): Boolean {
            popCalls += 1
            return true
        }
    }

    private class FakeTripRepository : TripRepository {
        override val trips: MutableStateFlow<List<TripDto>> = MutableStateFlow(emptyList())

        override fun getTrip(tripId: String): Flow<TripDto> = flowOf(
            TripDto(
                id = tripId,
                ownerId = "owner",
                title = "Trip",
                description = null,
                startDate = "2026-01-10",
                endDate = "2026-01-12",
                locationLine = null,
                coverUrl = null,
                currencyCode = "EUR",
                status = "active",
                updatedAt = "2026-03-16T10:00:00Z",
            )
        )

        override suspend fun refreshTrips(): Result<Unit> = Result.success(Unit)
        override suspend fun createTrip(request: CreateTripRequest): String = "trip-created"
        override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit> = Result.success(Unit)
        override suspend fun archiveTrip(tripId: String) = Unit
        override suspend fun deleteTrip(tripId: String) = Unit
        override fun tripMembers(tripId: String): Flow<List<MemberDto>> = flowOf(emptyList())
        override suspend fun removeMember(tripId: String, memberId: String) = Unit
    }

    private class FakeItineraryRepository(
        days: List<ItineraryDayDto>,
    ) : ItineraryRepository {
        private val itineraryFlow = MutableStateFlow(days)

        val refreshCalls = mutableListOf<String>()
        val trimCalls = mutableListOf<TrimOutOfRangeRequest>()
        var refreshResult: Result<Unit> = Result.success(Unit)
        var trimError: Throwable? = null

        override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> = itineraryFlow

        override fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>> = itineraryFlow

        override suspend fun refreshItinerary(tripId: String): Result<Unit> {
            refreshCalls += tripId
            return refreshResult
        }

        override suspend fun searchCities(tripId: String, query: String, limit: Int): List<CitySuggestionDto> = emptyList()

        override suspend fun searchPlaces(tripId: String, query: String, limit: Int): List<PlaceSuggestionDto> = emptyList()

        override suspend fun updateDay(dayId: String, request: UpdateDayRequest) = Unit

        override suspend fun updateDaysCity(tripId: String, dayIds: List<String>, request: UpdateDayRequest) = Unit

        override suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto =
            ActivityDto(
                id = "activity-1",
                dayId = dayId,
                title = request.title,
                timeText = request.timeText,
                locationName = request.locationName,
                link = request.link,
                costAmount = request.costAmount,
                costType = request.costType,
                notes = request.notes,
                orderIndex = request.orderIndex ?: 0,
            )

        override suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) = Unit

        override suspend fun moveActivity(activityId: String, request: MoveActivityRequest) = Unit

        override suspend fun deleteActivity(activityId: String) = Unit

        override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) = Unit

        override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) {
            trimCalls += request
            trimError?.let { throw it }
        }
    }

    private fun activity(title: String, orderIndex: Int): ActivityDto = ActivityDto(
        id = "activity-$orderIndex",
        dayId = "day-3",
        title = title,
        timeText = null,
        locationName = null,
        link = null,
        costAmount = null,
        costType = null,
        notes = null,
        orderIndex = orderIndex,
    )

    private fun day(
        id: String,
        dayNumber: Int,
        date: String,
        city: String?,
        isOutOfRange: Boolean,
        activities: List<ActivityDto> = emptyList(),
    ): ItineraryDayDto = ItineraryDayDto(
        id = id,
        tripId = "trip-1",
        date = date,
        dayNumber = dayNumber,
        city = city,
        cityProviderId = null,
        cityLat = if (city == null) null else 1.0,
        cityLon = if (city == null) null else 1.0,
        isOutOfRange = isOutOfRange,
        activities = activities,
    )
}
