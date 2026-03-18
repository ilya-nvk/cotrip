package nvk.cotrip.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.ui.common.UiErrorMapper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_profileState_when_screenRenders_then_rendersMainSections() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val viewModel = createViewModel(
            userRepository = SettingsFakeUserRepository(
                user = settingsUserDto(name = "Alice Cooper", photoUrl = null)
            ),
            notificationRepository = SettingsFakeNotificationRepository(),
        )

        // WHEN
        composeRule.setContent {
            SettingsScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(context.getString(R.string.settings_title), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_profile), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_add_photo), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_notifications_discussions), substring = true).assertIsDisplayed()
    }

    @Test
    fun given_deleteDialogState_when_screenRenders_then_rendersConfirmationDialog() {
        // GIVEN
        val context: Context = ApplicationProvider.getApplicationContext()
        val viewModel = createViewModel(
            userRepository = SettingsFakeUserRepository(),
            notificationRepository = SettingsFakeNotificationRepository(),
        )
        viewModel.onEvent(SettingsEvent.OnDeleteProfileClick)

        // WHEN
        composeRule.setContent {
            SettingsScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(context.getString(R.string.settings_delete_dialog_title), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_delete_dialog_message), substring = true).assertIsDisplayed()
    }

    private fun createViewModel(
        userRepository: SettingsFakeUserRepository,
        notificationRepository: SettingsFakeNotificationRepository,
    ): SettingsViewModel {
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        return SettingsViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            appNavigator = SettingsFakeNavigator(),
            authRepository = SettingsFakeAuthRepository(),
            userRepository = userRepository,
            imageUploadRepository = SettingsFakeImageUploadRepository(),
            notificationRepository = notificationRepository,
            apiCaller = ApiCaller(Json { ignoreUnknownKeys = true }),
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
