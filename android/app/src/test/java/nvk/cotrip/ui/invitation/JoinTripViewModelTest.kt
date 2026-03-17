package nvk.cotrip.ui.invitation

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class JoinTripViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_variousInputs_when_onInviteInputChange_then_acceptsTokenTripIdAndInviteUrls() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val inviteRepository = FakeInviteRepository()
        val tripRepository = FakeTripRepository()
        val navigator = FakeNavigator()
        val viewModel = createViewModel(
            inviteRepository = inviteRepository,
            tripRepository = tripRepository,
            navigator = navigator,
        )

        // WHEN / THEN
        val token = "a".repeat(32)
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange("  https://api.cotrip.site/invite/$token  "))
        assertTrue(viewModel.state.value.isInviteValid)

        viewModel.onEvent(JoinTripEvent.OnInviteInputChange("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(viewModel.state.value.isInviteValid)

        viewModel.onEvent(JoinTripEvent.OnInviteInputChange("bad-input"))
        assertFalse(viewModel.state.value.isInviteValid)
    }

    @Test
    fun given_invalidInput_when_joinClick_then_showsValidationToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel(
            inviteRepository = FakeInviteRepository(),
            tripRepository = FakeTripRepository(),
            navigator = FakeNavigator(),
        )
        val collected = mutableListOf<JoinTripEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange("invalid"))
        viewModel.onEvent(JoinTripEvent.OnJoinClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        val effect = collected.single()
        assertEquals(JoinTripEffect.ShowToastRes(R.string.join_trip_invalid), effect)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun given_validToken_when_joinByTokenSuccess_then_navigatesAndResetsLoading() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val token = "b".repeat(32)
        val inviteRepository = FakeInviteRepository().apply {
            inviteByToken[token] = inviteInfo(tripId = "trip-1")
            acceptResult = "trip-1"
        }
        val tripRepository = FakeTripRepository()
        val navigator = FakeNavigator()
        val viewModel = createViewModel(inviteRepository, tripRepository, navigator)
        val collected = mutableListOf<JoinTripEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange(token))
        viewModel.onEvent(JoinTripEvent.OnJoinClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(JoinTripEffect.ShowToastRes(R.string.join_trip_success), collected.single())
        assertEquals(Destination.TripDetails("trip-1"), navigator.lastDestination)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf(token), inviteRepository.acceptCalls)
    }

    @Test
    fun given_alreadyMember_when_joinByToken_then_showsAlreadyJoinedToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val token = "c".repeat(32)
        val inviteRepository = FakeInviteRepository().apply {
            inviteByToken[token] = inviteInfo(tripId = "joined-trip")
        }
        val tripRepository = FakeTripRepository(
            trips = MutableStateFlow(listOf(trip("joined-trip")))
        )
        val viewModel = createViewModel(inviteRepository, tripRepository, FakeNavigator())
        val collected = mutableListOf<JoinTripEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange(token))
        viewModel.onEvent(JoinTripEvent.OnJoinClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            JoinTripEffect.ShowToastRes(R.string.join_trip_already_joined),
            collected.single(),
        )
        assertTrue(inviteRepository.acceptCalls.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun given_joinPreflightFailure_when_joinClick_then_showsMappedErrorAndStopsLoading() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val token = "d".repeat(32)
        val inviteRepository = FakeInviteRepository().apply {
            inviteByToken[token] = inviteInfo(tripId = "trip-preflight")
            getInviteError = IOException("network down")
        }
        val viewModel = createViewModel(
            inviteRepository = inviteRepository,
            tripRepository = FakeTripRepository(),
            navigator = FakeNavigator(),
        )
        val collected = mutableListOf<JoinTripEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange(token))
        viewModel.onEvent(JoinTripEvent.OnJoinClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(
            JoinTripEffect.ShowToastRes(R.string.common_error_server_unreachable),
            collected.single(),
        )
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun given_joinByTripIdWithBlankServerResponse_when_joinClick_then_fallsBackToInputTripId() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val tripId = "550e8400-e29b-41d4-a716-446655440000"
        val inviteRepository = FakeInviteRepository().apply {
            joinByIdResult = ""
        }
        val navigator = FakeNavigator()
        val viewModel = createViewModel(
            inviteRepository = inviteRepository,
            tripRepository = FakeTripRepository(),
            navigator = navigator,
        )
        val collected = mutableListOf<JoinTripEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(collected)
        }

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnInviteInputChange(tripId))
        viewModel.onEvent(JoinTripEvent.OnJoinClick)
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(JoinTripEffect.ShowToastRes(R.string.join_trip_success), collected.single())
        assertEquals(Destination.TripDetails(tripId), navigator.lastDestination)
        assertEquals(listOf(tripId), inviteRepository.joinByIdCalls)
    }

    @Test
    fun given_deepLinkToken_when_init_then_autoJoinsAndCanonicalizesInput() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val token = "e".repeat(32)
        val inviteRepository = FakeInviteRepository().apply {
            inviteByToken[token] = inviteInfo(tripId = "deep-trip")
            acceptResult = "deep-trip"
        }
        val navigator = FakeNavigator()
        val viewModel = createViewModel(
            inviteRepository = inviteRepository,
            tripRepository = FakeTripRepository(),
            navigator = navigator,
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.JoinTrip.ARG_INVITE_TOKEN to "https://api.cotrip.site/invite/$token")
            ),
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        assertTrue(viewModel.state.value.isInviteValid)
        assertEquals("https://api.cotrip.site/invite/$token", viewModel.state.value.inviteInput)
        assertEquals(Destination.TripDetails("deep-trip"), navigator.lastDestination)
        assertEquals(listOf(token), inviteRepository.acceptCalls)
    }

    @Test
    fun given_screenOpen_when_backClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = FakeNavigator()
        val viewModel = createViewModel(
            inviteRepository = FakeInviteRepository(),
            tripRepository = FakeTripRepository(),
            navigator = navigator,
        )

        // WHEN
        viewModel.onEvent(JoinTripEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    private fun createViewModel(
        inviteRepository: InviteRepository,
        tripRepository: TripRepository,
        navigator: AppNavigator,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): JoinTripViewModel {
        return JoinTripViewModel(
            savedStateHandle = savedStateHandle,
            appNavigator = navigator,
            inviteRepository = inviteRepository,
            tripRepository = tripRepository,
            apiCaller = apiCaller,
            uiErrorMapper = uiErrorMapper,
        )
    }

    private fun inviteInfo(tripId: String): InviteInfoDto = InviteInfoDto(
        tripId = tripId,
        title = "Trip",
        startDate = "2026-01-10",
        endDate = "2026-01-12",
        locationLine = null,
        expiresAt = "2026-12-31T00:00:00Z",
    )

    private fun trip(id: String): TripDto = TripDto(
        id = id,
        ownerId = "owner",
        title = "Trip $id",
        description = null,
        startDate = "2026-01-10",
        endDate = "2026-01-12",
        locationLine = null,
        coverUrl = null,
        currencyCode = "EUR",
        status = "active",
        updatedAt = "2026-03-16T10:00:00Z",
    )

    private class FakeNavigator : AppNavigator {
        var lastDestination: Destination? = null
        var popCalls: Int = 0

        override fun navigate(destination: Destination, navOptions: ((androidx.navigation.NavOptionsBuilder.() -> Unit)?)) {
            lastDestination = destination
        }

        override fun popBackStack(): Boolean {
            popCalls += 1
            return true
        }
    }

    private class FakeInviteRepository : InviteRepository {
        val inviteByToken: MutableMap<String, InviteInfoDto> = mutableMapOf()
        var getInviteError: Throwable? = null
        var acceptResult: String = "trip-default"
        var joinByIdResult: String = "trip-default"
        val acceptCalls: MutableList<String> = mutableListOf()
        val joinByIdCalls: MutableList<String> = mutableListOf()

        override suspend fun createInvite(tripId: String): InviteLinkDto {
            return InviteLinkDto(
                token = "token",
                url = "https://api.cotrip.site/invite/token",
                expiresAt = "2026-12-31T00:00:00Z",
            )
        }

        override fun getInvite(token: String): Flow<InviteInfoDto> = flow {
            getInviteError?.let { throw it }
            emit(inviteByToken[token] ?: inviteInfo(tripId = "trip-from-$token"))
        }

        override suspend fun acceptInvite(token: String): String {
            acceptCalls += token
            return acceptResult
        }

        override suspend fun joinTripById(tripId: String): String {
            joinByIdCalls += tripId
            return joinByIdResult
        }

        private fun inviteInfo(tripId: String): InviteInfoDto = InviteInfoDto(
            tripId = tripId,
            title = "Trip",
            startDate = "2026-01-10",
            endDate = "2026-01-12",
            locationLine = null,
            expiresAt = "2026-12-31T00:00:00Z",
        )
    }

    private class FakeTripRepository(
        override val trips: MutableStateFlow<List<TripDto>> = MutableStateFlow(emptyList()),
    ) : TripRepository {
        override fun getTrip(tripId: String): Flow<TripDto> = flowOf(trip(tripId))

        override suspend fun refreshTrips(): Result<Unit> = Result.success(Unit)

        override suspend fun createTrip(request: CreateTripRequest): String = "trip-created"

        override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit> =
            Result.success(Unit)

        override suspend fun archiveTrip(tripId: String) = Unit

        override suspend fun deleteTrip(tripId: String) = Unit

        override fun tripMembers(tripId: String): Flow<List<MemberDto>> = flowOf(emptyList())

        override suspend fun removeMember(tripId: String, memberId: String) = Unit

        private fun trip(id: String): TripDto = TripDto(
            id = id,
            ownerId = "owner",
            title = "Trip $id",
            description = null,
            startDate = "2026-01-10",
            endDate = "2026-01-12",
            locationLine = null,
            coverUrl = null,
            currencyCode = "EUR",
            status = "active",
            updatedAt = "2026-03-16T10:00:00Z",
        )
    }
}
