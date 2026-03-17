package nvk.cotrip.ui.expense.details

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
class ExpenseDetailsScreenComposeTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun given_contentState_when_screenRenders_then_displaysExpenseTitle() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expense = expenseFormExpenseDto(id = "exp-1", title = "Dinner", amount = 50.0)
        val expenseRepository = ExpenseFormFakeExpenseRepository(expense = expense)
        expenseRepository.setExpenses(listOf(expense))
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        advanceUntilIdle()

        // WHEN
        composeRule.setContent {
            ExpenseDetailsScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // THEN
        composeRule.onNodeWithText("Dinner", substring = true).assertIsDisplayed()
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
            expense = expenseFormExpenseDto(id = "exp-1", amount = 20.0),
        ).apply { setExpenses(listOf(expenseFormExpenseDto(id = "exp-1", amount = 20.0))) },
        userRepository: ExpenseFormFakeUserRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
    ): ExpenseDetailsViewModel {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        return ExpenseDetailsViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Destination.ExpenseDetails.ARG_TRIP_ID to "trip-1",
                    Destination.ExpenseDetails.ARG_EXPENSE_ID to "exp-1",
                )
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
