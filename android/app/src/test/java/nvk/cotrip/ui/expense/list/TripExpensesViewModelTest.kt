package nvk.cotrip.ui.expense.list

import android.app.Application
import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripExpensesViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_expensesExist_when_init_then_loadsExpensesAndBuildsContentState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expenses = listOf(
            expenseFormExpenseDto(id = "exp-1", title = "Lunch", amount = 30.0),
            expenseFormExpenseDto(id = "exp-2", title = "Taxi", amount = 15.0),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository(initialExpensesList = expenses)
        val viewModel = createViewModel(expenseRepository = expenseRepository)

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertTrue(state is TripExpensesState.Content)
        val content = state as TripExpensesState.Content
        assertEquals(2, content.spent.size + content.planned.size)
        assertEquals("trip-1", content.tripId)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripExpensesEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_onAddExpenseClick_then_navigatesToCreateExpense() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripExpensesEvent.OnAddExpenseClick)

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.CreateExpense })
        assertEquals("trip-1", (navigator.destinations.single() as Destination.CreateExpense).tripId)
    }

    @Test
    fun given_contentShown_when_onExpenseClick_then_navigatesToExpenseDetails() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripExpensesEvent.OnExpenseClick("exp-1"))

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.ExpenseDetails })
        val details = navigator.destinations.filterIsInstance<Destination.ExpenseDetails>().single()
        assertEquals("trip-1", details.tripId)
        assertEquals("exp-1", details.expenseId)
    }

    @Test
    fun given_contentShown_when_onUserRefresh_then_runsRefresh() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expenseRepository = ExpenseFormFakeExpenseRepository(
            initialExpensesList = listOf(expenseFormExpenseDto(id = "exp-1", amount = 10.0)),
        )
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(TripExpensesEvent.OnUserRefresh)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertTrue(viewModel.state.value is TripExpensesState.Content)
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
