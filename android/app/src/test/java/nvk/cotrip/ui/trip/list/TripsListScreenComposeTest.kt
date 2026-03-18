package nvk.cotrip.ui.trip.list

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import nvk.cotrip.R
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.sync.SyncPullRepository
import nvk.cotrip.ui.navigation.AppNavigator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripsListScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_emptyState_when_screenRenders_then_showsCreateAndJoinCtas() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val createText = context.getString(R.string.create_trip)
        val joinText = context.getString(R.string.join_trip)
        val noTripsTitle = context.getString(R.string.no_trips_yet)
        val viewModel = createViewModel(trips = emptyList())

        // WHEN
        composeRule.setContent {
            TripsListScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(noTripsTitle, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(createText, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(joinText, substring = true).assertIsDisplayed()
    }

    @Test
    fun given_contentStateWithPastTrip_when_togglePastAndScreenRenders_then_showsSectionsAndExpandedPast() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val pastHeader = context.getString(R.string.section_past_with_count, 1)
        val today = LocalDate.now()
        val trips = listOf(
            trip(
                id = "past",
                title = "Past Trip",
                start = today.minusDays(8),
                end = today.minusDays(5),
            ),
        )
        val viewModel = createViewModel(trips = trips)
        viewModel.onEvent(TripsListEvent.OnTogglePast)

        // WHEN
        composeRule.setContent {
            TripsListScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(pastHeader, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Past Trip", substring = true).assertIsDisplayed()
    }

    private fun createViewModel(trips: List<TripDto>): TripsListViewModel {
        val tripRepository = mockk<TripRepository>()
        val syncPullRepository = mockk<SyncPullRepository>()
        val appNavigator = mockk<AppNavigator>(relaxed = true)

        every { tripRepository.trips } returns MutableStateFlow(trips)
        every { tripRepository.tripMembers(any()) } returns flowOf(
            listOf(
                MemberDto(
                    userId = "u1",
                    name = "User One",
                    initials = "UO",
                    role = "member",
                    status = "accepted",
                    photoUrl = null,
                )
            )
        )
        coEvery { tripRepository.refreshTrips() } returns Result.success(Unit)
        coEvery { syncPullRepository.pull() } returns Result.success(Unit)

        return TripsListViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            appNavigator = appNavigator,
            tripRepository = tripRepository,
            syncPullRepository = syncPullRepository,
        )
    }

    private fun trip(
        id: String,
        title: String,
        start: LocalDate,
        end: LocalDate,
    ): TripDto = TripDto(
        id = id,
        ownerId = "owner",
        title = title,
        description = null,
        startDate = start.toString(),
        endDate = end.toString(),
        locationLine = "Paris",
        coverUrl = null,
        currencyCode = "EUR",
        status = "active",
        updatedAt = "2026-03-16T10:00:00Z",
    )
}
