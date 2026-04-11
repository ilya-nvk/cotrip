package nvk.cotrip.ui.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.TextInputLimits
import nvk.cotrip.ui.common.UiErrorMapper
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_reposReturnData_when_init_then_loadsProfileAndNotificationSettings() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel(
            userRepository = SettingsFakeUserRepository(
                user = settingsUserDto(name = "Alice Cooper", photoUrl = null)
            ),
            notificationRepository = SettingsFakeNotificationRepository(
                initialSettings = listOf(
                    NotificationSettingDto("discussions_comments", true),
                    NotificationSettingDto("expenses_new", false),
                    NotificationSettingDto("expenses_settlements", true),
                )
            ),
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Alice Cooper", state.profile.name)
        assertFalse(state.profile.hasPhoto)
        assertFalse(state.notificationSections.flatMap { it.items }.first { it.key == "expenses_new" }.enabled)
    }

    @Test
    fun given_nameChange_when_saveClick_then_updatesProfileAndResetsSaveFlag() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val userRepository = SettingsFakeUserRepository(
            user = settingsUserDto(name = "Alice Cooper", photoUrl = null)
        )
        val viewModel = createViewModel(
            userRepository = userRepository,
            notificationRepository = SettingsFakeNotificationRepository(),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(
            SettingsEvent.OnNameChange(
                "B".repeat(TextInputLimits.SETTINGS_NAME + 20)
            )
        )
        val changed = viewModel.state.value
        assertEquals(TextInputLimits.SETTINGS_NAME, changed.profile.name.length)
        assertTrue(changed.canSave)

        viewModel.onEvent(SettingsEvent.OnSaveClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, userRepository.updateRequests.size)
        assertEquals(TextInputLimits.SETTINGS_NAME, userRepository.updateRequests.single().name.length)
        assertFalse(viewModel.state.value.canSave)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun given_toggleNotificationsFailure_when_onToggleNotifications_then_showsErrorToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val notificationRepository = SettingsFakeNotificationRepository().apply {
            updateSettingsResult = Result.failure(IOException("offline"))
        }
        val viewModel = createViewModel(
            userRepository = SettingsFakeUserRepository(),
            notificationRepository = notificationRepository,
        )
        advanceUntilIdle()

        val effects = mutableListOf<SettingsEffect>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }
        // WHEN
        viewModel.onEvent(SettingsEvent.OnToggleNotifications("expenses_new", false))
        advanceUntilIdle()
        collector.join()

        // THEN
        assertEquals(SettingsEffect.ShowToastRes(R.string.common_error_server_unreachable), effects.single())
    }

    @Test
    fun given_photoPicked_when_uploadSuccess_then_updatesPhoto() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val imageUploadRepository = SettingsFakeImageUploadRepository().apply {
            uploadResult = "https://cdn.example/new-avatar.jpg"
        }
        val viewModel = createViewModel(
            userRepository = SettingsFakeUserRepository(),
            notificationRepository = SettingsFakeNotificationRepository(),
            imageUploadRepository = imageUploadRepository,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(SettingsEvent.OnPhotoPicked("content://photo/1"))
        advanceUntilIdle()

        // THEN
        val profile = viewModel.state.value.profile
        assertTrue(profile.hasPhoto)
        assertEquals("https://cdn.example/new-avatar.jpg", profile.photoUrl)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun given_confirmDelete_when_deleteProfileSuccess_then_clearsSessionAndNavigatesToSignIn() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val userRepository = SettingsFakeUserRepository()
        val navigator = SettingsFakeNavigator()
        val viewModel = createViewModel(
            userRepository = userRepository,
            notificationRepository = SettingsFakeNotificationRepository(),
            navigator = navigator,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(SettingsEvent.OnDeleteProfileClick)
        assertTrue(viewModel.state.value.showDeleteDialog)
        viewModel.onEvent(SettingsEvent.OnConfirmDeleteProfileClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, userRepository.deleteCalls)
        assertEquals(1, userRepository.clearSessionCalls)
        assertTrue(navigator.destinations.contains(Destination.SignIn))
        assertFalse(viewModel.state.value.showDeleteDialog)
    }

    @Test
    fun given_screenOpen_when_logoutClick_then_callsAuthRepositoryAndNavigatesToSignIn() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val authRepository = SettingsFakeAuthRepository()
        val navigator = SettingsFakeNavigator()
        val viewModel = createViewModel(
            userRepository = SettingsFakeUserRepository(),
            notificationRepository = SettingsFakeNotificationRepository(),
            authRepository = authRepository,
            navigator = navigator,
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(SettingsEvent.OnLogoutClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, authRepository.logoutCalls)
        assertEquals(1, authRepository.clearSessionCalls)
        assertTrue(navigator.destinations.contains(Destination.SignIn))
    }

    private fun createViewModel(
        userRepository: SettingsFakeUserRepository,
        notificationRepository: SettingsFakeNotificationRepository,
        imageUploadRepository: SettingsFakeImageUploadRepository = SettingsFakeImageUploadRepository(),
        authRepository: SettingsFakeAuthRepository = SettingsFakeAuthRepository(),
        navigator: SettingsFakeNavigator = SettingsFakeNavigator(),
    ): SettingsViewModel {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        return SettingsViewModel(
            appContext = appContext,
            appNavigator = navigator,
            authRepository = authRepository,
            userRepository = userRepository,
            imageUploadRepository = imageUploadRepository,
            notificationRepository = notificationRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
