package nvk.cotrip.ui.invitation

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto
import nvk.cotrip.data.repository.InviteRepository
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
import java.time.OffsetDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InvitePeopleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_successWithInvalidExpiresAt_when_init_then_usesFallbackHours() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val inviteRepository = FakeInviteRepository().apply {
            createInviteResult = InviteLinkDto(
                token = "token",
                url = "https://api.cotrip.site/invite/token",
                expiresAt = "bad-date",
            )
        }

        val viewModel = createViewModel(
            navigator = navigator,
            inviteRepository = inviteRepository,
            tripId = "trip-1",
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        val content = viewModel.state.value as InvitePeopleState.Content
        assertEquals("trip-1", content.tripId)
        assertEquals("https://api.cotrip.site/invite/token", content.inviteLink)
        assertEquals(12, content.expiresInHours)
    }

    @Test
    fun given_successWithValidExpiresAt_when_init_then_mapsRemainingHours() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val inviteRepository = FakeInviteRepository().apply {
            createInviteResult = InviteLinkDto(
                token = "token",
                url = "https://api.cotrip.site/invite/token",
                expiresAt = OffsetDateTime.now().plusHours(5).toString(),
            )
        }

        val viewModel = createViewModel(
            navigator = navigator,
            inviteRepository = inviteRepository,
            tripId = "trip-2",
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        val content = viewModel.state.value as InvitePeopleState.Content
        assertTrue(content.expiresInHours in 4..5)
    }

    @Test
    fun given_contentShown_when_onCopyClick_then_emitsCopyEffect() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel(
            navigator = FakeNavigator(),
            inviteRepository = FakeInviteRepository(),
            tripId = "trip-3",
        )
        advanceUntilIdle()

        val collected = mutableListOf<InvitePeopleEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(InvitePeopleEvent.OnCopyClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            listOf(InvitePeopleEffect.CopyToClipboard("https://api.cotrip.site/invite/token")),
            collected,
        )
    }

    @Test
    fun given_contentShown_when_onShareClick_then_emitsShareEffect() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel(
            navigator = FakeNavigator(),
            inviteRepository = FakeInviteRepository(),
            tripId = "trip-4",
        )
        advanceUntilIdle()
        val collected = mutableListOf<InvitePeopleEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(InvitePeopleEvent.OnShareClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            InvitePeopleEffect.ShareText("https://api.cotrip.site/invite/token"),
            collected.single(),
        )
    }

    @Test
    fun given_screenOpen_when_onCloseClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val viewModel = createViewModel(
            navigator = navigator,
            inviteRepository = FakeInviteRepository(),
            tripId = "trip-5",
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(InvitePeopleEvent.OnCloseClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_initFailure_when_loadCompletes_then_showsUnavailable() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val inviteRepository = FakeInviteRepository().apply {
            createInviteError = IOException("network down")
        }
        val viewModel = createViewModel(
            navigator = FakeNavigator(),
            inviteRepository = inviteRepository,
            tripId = "trip-6",
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        assertEquals(InvitePeopleState.Unavailable, viewModel.state.value)
    }

    private fun createViewModel(
        navigator: AppNavigator,
        inviteRepository: InviteRepository,
        tripId: String,
    ): InvitePeopleViewModel {
        return InvitePeopleViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.InviteTravelers.ARG_TRIP_ID to tripId)
            ),
            appNavigator = navigator,
            inviteRepository = inviteRepository,
            apiCaller = apiCaller,
            uiErrorMapper = uiErrorMapper,
        )
    }

    private class FakeNavigator : AppNavigator {
        var popCalls: Int = 0

        override fun navigate(
            destination: Destination,
            navOptions: (androidx.navigation.NavOptionsBuilder.() -> Unit)?,
        ) = Unit

        override fun popBackStack(): Boolean {
            popCalls += 1
            return true
        }
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

        override suspend fun getInviteForJoin(token: String): InviteInfoDto =
            InviteInfoDto(
                tripId = "trip",
                title = "Trip",
                startDate = "2026-01-10",
                endDate = "2026-01-12",
                locationLine = null,
                expiresAt = "2026-12-31T00:00:00Z",
            )

        override suspend fun acceptInvite(token: String): String = "trip"

        override suspend fun joinTripById(tripId: String): String = tripId
    }
}
