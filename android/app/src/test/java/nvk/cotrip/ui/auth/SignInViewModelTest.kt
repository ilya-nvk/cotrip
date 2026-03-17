package nvk.cotrip.ui.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.AuthResponse
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.refresh.RefreshScheduler
import nvk.cotrip.data.repository.AuthRepository
import nvk.cotrip.notifications.PushTokenSyncManager
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.details.TripDetailsFakeNavigator
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
class SignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val uiErrorMapper = UiErrorMapper(networkStateProvider)
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_hasSession_when_init_then_navigatesToTrips() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val authRepository = mockk<AuthRepository>()
        every { authRepository.hasSession() } returns true
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            authRepository = authRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.Trips })
    }

    @Test
    fun given_noSession_when_init_then_doesNotNavigate() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val authRepository = mockk<AuthRepository>()
        every { authRepository.hasSession() } returns false
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            authRepository = authRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.isEmpty())
    }

    @Test
    fun given_noSessionAndSignInSucceeds_when_onGoogleIdToken_then_navigatesToTrips() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val authRepository = mockk<AuthRepository>()
        every { authRepository.hasSession() } returns false
        coEvery { authRepository.signInWithGoogle(any()) } returns authResponse()
        val refreshScheduler = mockk<RefreshScheduler>()
        every { refreshScheduler.scheduleImmediate() } returns Unit
        val pushTokenSyncManager = mockk<PushTokenSyncManager>()
        coEvery { pushTokenSyncManager.syncCurrentToken() } returns Unit
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            authRepository = authRepository,
            refreshScheduler = refreshScheduler,
            pushTokenSyncManager = pushTokenSyncManager,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(SignInEvent.StartGoogleSignIn)
        viewModel.onEvent(SignInEvent.OnGoogleIdToken("id-token-123"))
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.Trips })
    }

    @Test
    fun given_signInFails_when_onGoogleIdToken_then_stopsLoadingAndDoesNotNavigate() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val authRepository = mockk<AuthRepository>()
        every { authRepository.hasSession() } returns false
        coEvery { authRepository.signInWithGoogle(any()) } throws IOException("server error")
        val navigator = TripDetailsFakeNavigator()
        val viewModel = createViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            navigator = navigator,
            authRepository = authRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(SignInEvent.StartGoogleSignIn)
        viewModel.onEvent(SignInEvent.OnGoogleIdToken("id-token-123"))
        advanceUntilIdle()

        // THEN
        assertTrue(navigator.destinations.isEmpty())
        assertTrue(!viewModel.uiState.value.isLoading)
    }

    private fun authResponse(): AuthResponse = AuthResponse(
        accessToken = "access",
        refreshToken = "refresh",
        user = UserDto(
            id = "user-1",
            name = "Test User",
            photoUrl = null,
            initials = "TU",
        ),
    )

    private fun createViewModel(
        appContext: android.content.Context,
        navigator: TripDetailsFakeNavigator,
        authRepository: AuthRepository,
        refreshScheduler: RefreshScheduler = mockk { every { scheduleImmediate() } returns Unit },
        pushTokenSyncManager: PushTokenSyncManager = mockk { coEvery { syncCurrentToken() } returns Unit },
    ): SignInViewModel = SignInViewModel(
        appContext = appContext,
        navigator = navigator,
        authRepository = authRepository,
        refreshScheduler = refreshScheduler,
        pushTokenSyncManager = pushTokenSyncManager,
        apiCaller = apiCaller,
        networkStateProvider = networkStateProvider,
        uiErrorMapper = uiErrorMapper,
    )
}
