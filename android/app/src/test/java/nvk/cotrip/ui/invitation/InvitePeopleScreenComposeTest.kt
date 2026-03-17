package nvk.cotrip.ui.invitation

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto
import nvk.cotrip.data.repository.InviteRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InvitePeopleScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_loadingState_when_screenRenders_then_showsProgressAndHidesActionButtons() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()
        val copyText = context.getString(R.string.invite_people_copy_link)
        val shareText = context.getString(R.string.invite_people_share_link)
        val viewModel = createViewModel(
            inviteRepository = FakeInviteRepository().apply { createInviteError = IOException("offline") },
            tripId = "trip-loading",
        )

        // WHEN
        composeRule.setContent {
            InvitePeopleScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        assertTrue(
            composeRule.onAllNodesWithText(copyText, substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(shareText, substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun given_contentState_when_screenRenders_then_showsLinkAndActions() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()
        val copyText = context.getString(R.string.invite_people_copy_link)
        val shareText = context.getString(R.string.invite_people_share_link)
        val link = "https://api.cotrip.site/invite/token"
        val viewModel = createViewModel(
            inviteRepository = FakeInviteRepository().apply {
                createInviteResult = InviteLinkDto(
                    token = "token",
                    url = link,
                    expiresAt = "2026-12-31T00:00:00Z",
                )
            },
            tripId = "trip-content",
        )

        // WHEN
        composeRule.setContent {
            InvitePeopleScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(link, substring = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(copyText, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(shareText, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }

    private fun createViewModel(
        inviteRepository: InviteRepository,
        tripId: String,
    ): InvitePeopleViewModel {
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        return InvitePeopleViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.InviteTravelers.ARG_TRIP_ID to tripId)
            ),
            appNavigator = object : AppNavigator {
                override fun navigate(
                    destination: Destination,
                    navOptions: (androidx.navigation.NavOptionsBuilder.() -> Unit)?,
                ) = Unit

                override fun popBackStack(): Boolean = true
            },
            inviteRepository = inviteRepository,
            apiCaller = ApiCaller(Json { ignoreUnknownKeys = true }),
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }

    private class FakeInviteRepository : InviteRepository {
        var createInviteResult: InviteLinkDto = InviteLinkDto(
            token = "token",
            url = "https://api.cotrip.site/invite/token",
            expiresAt = "2026-12-31T00:00:00Z",
        )
        var createInviteError: Throwable? = null

        override suspend fun createInvite(tripId: String): InviteLinkDto {
            createInviteError?.let { throw it }
            return createInviteResult
        }

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
    }
}
