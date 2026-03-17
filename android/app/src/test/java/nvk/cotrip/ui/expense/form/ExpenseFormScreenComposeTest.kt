package nvk.cotrip.ui.expense.form

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpenseFormScreenComposeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_createModeAfterLoad_when_screenRenders_then_rendersTitleAndSaveButton() {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val context: Context = ApplicationProvider.getApplicationContext()
        val viewModel = createCreateViewModel()

        // WHEN
        composeRule.setContent {
            CreateExpenseScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(context.getString(R.string.expense_form_primary_save), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.expense_form_title_add), substring = true).assertIsDisplayed()
    }

    @Test
    fun given_createMode_when_switchToCustomAmounts_then_updatesState() {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createCreateViewModel()

        composeRule.setContent {
            CreateExpenseScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnSplitTypeChange(ExpenseSplitType.CustomAmounts))
        composeRule.waitForIdle()

        // THEN
        assertEquals(ExpenseSplitType.CustomAmounts, viewModel.state.value.splitType)
    }

    @Test
    fun given_editMode_when_screenRenders_then_rendersDeleteButton() {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val context: Context = ApplicationProvider.getApplicationContext()
        val viewModel = createEditViewModel()

        // WHEN
        composeRule.setContent {
            EditExpenseScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(context.getString(R.string.expense_form_title_edit), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.expense_form_delete), substring = true).assertIsDisplayed()
    }

    @Test
    fun given_createMode_when_screenRenders_then_doesNotShowDeleteButton() {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val context: Context = ApplicationProvider.getApplicationContext()
        val viewModel = createCreateViewModel()

        // WHEN
        composeRule.setContent {
            CreateExpenseScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText(context.getString(R.string.expense_form_title_add), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.expense_form_delete), substring = true).assertDoesNotExist()
    }

    private fun createCreateViewModel(): CreateExpenseViewModel {
        return CreateExpenseViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.CreateExpense.ARG_TRIP_ID to "trip-1")
            ),
            appNavigator = ExpenseFormFakeNavigator(),
            tripRepository = ExpenseFormFakeTripRepository(
                trip = expenseFormTripDto(),
                members = listOf(
                    expenseFormMemberDto(id = "user-1", name = "Alice"),
                    expenseFormMemberDto(id = "user-2", name = "Bob"),
                ),
            ),
            expenseRepository = ExpenseFormFakeExpenseRepository(),
            userRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }

    private fun createEditViewModel(): EditExpenseViewModel {
        return EditExpenseViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Destination.EditExpense.ARG_TRIP_ID to "trip-1",
                    Destination.EditExpense.ARG_EXPENSE_ID to "exp-1",
                )
            ),
            appNavigator = ExpenseFormFakeNavigator(),
            tripRepository = ExpenseFormFakeTripRepository(
                trip = expenseFormTripDto(),
                members = listOf(
                    expenseFormMemberDto(id = "user-1", name = "Alice"),
                    expenseFormMemberDto(id = "user-2", name = "Bob"),
                ),
            ),
            expenseRepository = ExpenseFormFakeExpenseRepository(
                expense = expenseFormExpenseDto(id = "exp-1", title = "Dinner", amount = 30.0),
            ),
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
