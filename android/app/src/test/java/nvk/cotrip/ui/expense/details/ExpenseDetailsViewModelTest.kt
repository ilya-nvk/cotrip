package nvk.cotrip.ui.expense.details

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
import nvk.cotrip.data.network.dto.ExpenseParticipantDto
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpenseDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = nvk.cotrip.testing.MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_expenseExists_when_init_then_loadsExpenseAndBuildsContentState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expense = expenseFormExpenseDto(
            id = "exp-1",
            tripId = "trip-1",
            title = "Dinner",
            amount = 50.0,
            status = "paid",
            paidById = "user-1",
            participants = listOf(
                ExpenseParticipantDto("user-1", 25.0, true, true, "Alice"),
                ExpenseParticipantDto("user-2", 25.0, true, false, "Bob"),
            ),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository(expense = expense)
        expenseRepository.setExpenses(listOf(expense))
        val viewModel = createViewModel(
            tripRepository = ExpenseFormFakeTripRepository(
                trip = expenseFormTripDto(id = "trip-1"),
                members = listOf(
                    expenseFormMemberDto(id = "user-1", name = "Alice"),
                    expenseFormMemberDto(id = "user-2", name = "Bob"),
                ),
            ),
            expenseRepository = expenseRepository,
            userRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertTrue(state is ExpenseDetailsState.Content)
        val content = state as ExpenseDetailsState.Content
        assertEquals("Dinner", content.title)
        assertEquals("exp-1", content.expenseId)
        assertEquals("trip-1", content.tripId)
        assertEquals(ExpenseDetailsStatus.Unsettled, content.status)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseDetailsEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_screenOpen_when_onEditClick_then_navigatesToEditExpense() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseDetailsEvent.OnEditClick)

        // THEN
        assertTrue(navigator.destinations.any { it is Destination.EditExpense })
        val edit = navigator.destinations.filterIsInstance<Destination.EditExpense>().single()
        assertEquals("trip-1", edit.tripId)
        assertEquals("exp-1", edit.expenseId)
    }

    @Test
    fun given_contentShown_when_onRefresh_then_succeedsWithoutToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseDetailsEvent.OnRefresh)
        advanceUntilIdle()

        // THEN
        assertTrue(viewModel.state.value is ExpenseDetailsState.Content)
    }

    @Test
    fun given_plannedExpense_when_onMarkAsPaidClick_then_updatesExpenseToPaid() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expense = expenseFormExpenseDto(
            id = "exp-1",
            title = "Lunch",
            amount = 30.0,
            status = "planned",
            paidById = null,
            date = null,
            participants = listOf(
                ExpenseParticipantDto("user-1", 15.0, true, false, "Alice"),
                ExpenseParticipantDto("user-2", 15.0, true, false, "Bob"),
            ),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository(expense = expense)
        expenseRepository.setExpenses(listOf(expense))
        val viewModel = createViewModel(
            expenseRepository = expenseRepository,
            userRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseDetailsEvent.OnMarkAsPaidClick)
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, expenseRepository.updateExpenseCalls.size)
        assertEquals("paid", expenseRepository.updateExpenseCalls.single().second.status)
        assertEquals("user-1", expenseRepository.updateExpenseCalls.single().second.paidById)
    }

    @Test
    fun given_paidExpense_when_onMarkParticipantPaidClick_then_updatesParticipant() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expense = expenseFormExpenseDto(
            id = "exp-1",
            amount = 50.0,
            status = "paid",
            paidById = "user-1",
            participants = listOf(
                ExpenseParticipantDto("user-1", 25.0, true, false, "Alice"),
                ExpenseParticipantDto("user-2", 25.0, true, false, "Bob"),
            ),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository(expense = expense)
        expenseRepository.setExpenses(listOf(expense))
        val viewModel = createViewModel(
            expenseRepository = expenseRepository,
            userRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseDetailsEvent.OnMarkParticipantPaidClick("user-1"))
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, expenseRepository.updateExpenseCalls.size)
        val participants = expenseRepository.updateExpenseCalls.single().second.participants!!
        assertTrue(participants.single { it.userId == "user-1" }.isPaid)
    }

    @Test
    fun given_participantPaid_when_onUnmarkParticipantPaidClick_then_updatesParticipant() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expense = expenseFormExpenseDto(
            id = "exp-1",
            amount = 50.0,
            status = "paid",
            paidById = "user-1",
            participants = listOf(
                ExpenseParticipantDto("user-1", 25.0, true, true, "Alice"),
                ExpenseParticipantDto("user-2", 25.0, true, false, "Bob"),
            ),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository(expense = expense)
        expenseRepository.setExpenses(listOf(expense))
        val viewModel = createViewModel(
            expenseRepository = expenseRepository,
            userRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
        )
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseDetailsEvent.OnUnmarkParticipantPaidClick("user-1"))
        advanceUntilIdle()
        advanceUntilIdle()

        // THEN
        assertEquals(1, expenseRepository.updateExpenseCalls.size)
        val participants = expenseRepository.updateExpenseCalls.single().second.participants!!
        assertTrue(!participants.single { it.userId == "user-1" }.isPaid)
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
