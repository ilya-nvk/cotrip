package nvk.cotrip.ui.invitation

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.repository.InviteRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class JoinTripScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_inputChange_when_screenRenders_then_buttonStateAndValidationMessage_followInputValidity() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()
        val joinText = context.getString(R.string.join_trip_action)
        val invalidText = context.getString(R.string.join_trip_invalid)
        val viewModel = createViewModel()

        // WHEN
        composeRule.setContent {
            JoinTripScreen(viewModel = viewModel)
        }

        // THEN
        assertJoinButtonEnabledState(joinText, expectedEnabled = false)

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange("bad"))
        composeRule.waitForIdle()
        // THEN
        composeRule.onNodeWithText(invalidText, substring = true).assertIsDisplayed()
        assertJoinButtonEnabledState(joinText, expectedEnabled = false)

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange("550e8400-e29b-41d4-a716-446655440000"))
        composeRule.waitForIdle()
        // THEN
        assertJoinButtonEnabledState(joinText, expectedEnabled = true)
    }

    private fun assertJoinButtonEnabledState(
        joinText: String,
        expectedEnabled: Boolean,
    ) {
        val nodes = composeRule.onAllNodesWithText(joinText, substring = true).fetchSemanticsNodes()
        val buttonNodes = nodes.filter { node ->
            runCatching { node.config[SemanticsProperties.Role] }.getOrNull() == Role.Button
        }
        assertTrue(buttonNodes.isNotEmpty())
        val hasEnabledButton = buttonNodes.any { node ->
            runCatching { node.config[SemanticsProperties.Disabled] }.getOrNull() == null
        }
        if (expectedEnabled) {
            assertTrue(hasEnabledButton)
        } else {
            assertTrue(!hasEnabledButton)
        }
    }

    private fun createViewModel(): JoinTripViewModel {
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        return JoinTripViewModel(
            savedStateHandle = SavedStateHandle(),
            appNavigator = object : AppNavigator {
                override fun navigate(
                    destination: Destination,
                    navOptions: (androidx.navigation.NavOptionsBuilder.() -> Unit)?,
                ) = Unit

                override fun popBackStack(): Boolean = true
            },
            inviteRepository = object : InviteRepository {
                override suspend fun createInvite(tripId: String): InviteLinkDto = InviteLinkDto(
                    token = "token",
                    url = "https://api.cotrip.site/invite/token",
                    expiresAt = "2026-12-31T00:00:00Z",
                )

                override fun getInvite(token: String): Flow<InviteInfoDto> = flowOf(
                    InviteInfoDto(
                        tripId = "trip",
                        title = "Trip",
                        startDate = "2026-01-10",
                        endDate = "2026-01-12",
                        locationLine = null,
                        expiresAt = "2026-12-31T00:00:00Z",
                    )
                )

                override suspend fun acceptInvite(token: String): String = "trip"

                override suspend fun joinTripById(tripId: String): String = tripId
            },
            tripRepository = object : TripRepository {
                override val trips: Flow<List<TripDto>> = MutableStateFlow(emptyList())

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
                override suspend fun createTrip(request: CreateTripRequest): String = "trip"
                override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit> = Result.success(Unit)
                override suspend fun archiveTrip(tripId: String) = Unit
                override suspend fun deleteTrip(tripId: String) = Unit
                override fun tripMembers(tripId: String): Flow<List<MemberDto>> = flowOf(emptyList())
                override suspend fun removeMember(tripId: String, memberId: String) = Unit
            },
            apiCaller = ApiCaller(Json { ignoreUnknownKeys = true }),
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
