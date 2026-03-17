package nvk.cotrip.ui.expense.list

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.expense.form.ExpenseFormFakeExpenseRepository
import nvk.cotrip.ui.expense.form.ExpenseFormFakeNavigator
import nvk.cotrip.ui.expense.form.ExpenseFormFakeTripRepository
import nvk.cotrip.ui.expense.form.ExpenseFormFakeUserRepository
import nvk.cotrip.ui.expense.form.expenseFormExpenseDto
import nvk.cotrip.ui.expense.form.expenseFormMemberDto
import nvk.cotrip.ui.expense.form.expenseFormTripDto
import nvk.cotrip.ui.expense.form.expenseFormUserDto
import nvk.cotrip.ui.navigation.Destination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripExpensesScreenComposeTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun given_contentState_when_screenRenders_then_displaysSummaryAndExpense() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expenses = listOf(
            expenseFormExpenseDto(id = "exp-1", title = "Lunch", amount = 25.0),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository(initialExpensesList = expenses)
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        advanceUntilIdle()

        // WHEN
        composeRule.setContent {
            TripExpensesScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText("Lunch", substring = true).assertIsDisplayed()
        val context: Context = ApplicationProvider.getApplicationContext()
        composeRule.onNodeWithText(context.getString(R.string.trip_expenses_balance_settled), substring = true).assertIsDisplayed()
    }

    private fun createViewModel(
        navigator: ExpenseFormFakeNavigator = ExpenseFormFakeNavigator(),
        tripRepository: ExpenseFormFakeTripRepository = ExpenseFormFakeTripRepository(
            trip = expenseFormTripDto(id = "trip-1"),
            members = listOf(
                expenseFormMemberDto(id = "user-1", name = "Alice"),
                expenseFormMemberDto(id = "user-2", name = "Bob"),
            ),
        ),
        expenseRepository: ExpenseFormFakeExpenseRepository = ExpenseFormFakeExpenseRepository(
            initialExpensesList = listOf(expenseFormExpenseDto(id = "exp-1", amount = 20.0)),
        ),
        userRepository: ExpenseFormFakeUserRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
    ): TripExpensesViewModel {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        return TripExpensesViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.Expenses.ARG_TRIP_ID to "trip-1")
            ),
            appContext = appContext,
            appNavigator = navigator,
            tripRepository = tripRepository,
            expenseRepository = expenseRepository,
            userRepository = userRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
