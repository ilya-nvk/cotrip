package nvk.cotrip.ui.outofrangedays

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OutOfRangeDaysScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_contentState_when_screenRenders_then_rendersHeaderDaysAndActions() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()
        val title = context.getString(R.string.out_of_range_days_title)
        val remove = context.getString(R.string.out_of_range_days_remove, 1)
        val viewModel = createViewModel(
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

        // WHEN
        composeRule.setContent {
            OutOfRangeDaysScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(title, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Day 3", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Rome", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(remove, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Move trip end date to", substring = true).assertIsDisplayed()
    }

    @Test
    fun given_noCityAndNoActivities_when_screenRenders_then_renderFallbackTexts() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()
        val noCity = context.getString(R.string.out_of_range_days_no_city)
        val noActivities = context.getString(R.string.out_of_range_days_activities_none)
        val viewModel = createViewModel(
            days = listOf(
                day(
                    id = "day-4",
                    dayNumber = 4,
                    date = "2026-01-14",
                    city = null,
                    isOutOfRange = true,
                    activities = emptyList(),
                )
            )
        )

        // WHEN
        composeRule.setContent {
            OutOfRangeDaysScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(noCity, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(noActivities, substring = true).assertIsDisplayed()
    }

    private fun createViewModel(days: List<ItineraryDayDto>): OutOfRangeDaysViewModel {
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true

        return OutOfRangeDaysViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.OutOfRangeDays.ARG_TRIP_ID to "trip-1")
            ),
            appNavigator = object : AppNavigator {
                override fun navigate(destination: Destination, navOptions: (androidx.navigation.NavOptionsBuilder.() -> Unit)?) = Unit
                override fun popBackStack(): Boolean = true
            },
            tripRepository = object : TripRepository {
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
            },
            itineraryRepository = object : ItineraryRepository {
                private val flow = MutableStateFlow(days)

                override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> = flow
                override fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>> = flow
                override suspend fun refreshItinerary(tripId: String): Result<Unit> = Result.success(Unit)
                override suspend fun searchCities(tripId: String, query: String, limit: Int): List<CitySuggestionDto> = emptyList()
                override suspend fun searchPlaces(tripId: String, query: String, limit: Int): List<PlaceSuggestionDto> = emptyList()
                override suspend fun updateDay(dayId: String, request: UpdateDayRequest) = Unit
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
                override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) = Unit
            },
            apiCaller = ApiCaller(Json { ignoreUnknownKeys = true }),
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
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
        activities: List<ActivityDto>,
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
