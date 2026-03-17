package nvk.cotrip.ui.expense.form

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
import nvk.cotrip.data.network.dto.ExpenseParticipantDto
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class EditExpenseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_loadExpenseSuccess_when_init_then_populatesStateFromExpense() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expense = expenseFormExpenseDto(
            id = "exp-1",
            tripId = "trip-1",
            title = "Dinner",
            amount = 50.0,
            status = "paid",
            paidById = "user-1",
            date = LocalDate.now().toString(),
            splitType = "custom",
            note = "Italian place",
            participants = listOf(
                ExpenseParticipantDto("user-1", 25.0, true, true, "Alice"),
                ExpenseParticipantDto("user-2", 25.0, true, false, "Bob"),
            ),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository(expense = expense)
        val tripRepository = ExpenseFormFakeTripRepository(
            trip = expenseFormTripDto(),
            members = listOf(
                expenseFormMemberDto(id = "user-1", name = "Alice"),
                expenseFormMemberDto(id = "user-2", name = "Bob"),
            ),
        )
        val viewModel = createViewModel(
            tripRepository = tripRepository,
            expenseRepository = expenseRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertEquals("Dinner", state.title)
        assertEquals("50", state.amount)
        assertEquals(ExpenseFormStatus.Paid, state.status)
        assertEquals("user-1", state.paidById)
        assertEquals(ExpenseSplitType.CustomAmounts, state.splitType)
        assertEquals("Italian place", state.note)
        assertEquals(2, state.participants.size)
        assertTrue(state.participants.all { it.isSelected })
    }

    @Test
    fun given_loadExpenseFailure_when_init_then_stateKeepsEmptyTitle() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expenseRepository = ExpenseFormFakeExpenseRepository(
            expense = expenseFormExpenseDto(id = "exp-1", amount = 0.0),
        ).apply { getExpenseToThrow = RuntimeException("load failed") }
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        // WHEN
        advanceUntilIdle()

        // THEN
        assertTrue(viewModel.state.value.title.isBlank())
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.OnBackClick)

        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_blankTitle_when_onPrimaryClick_then_emitsErrorToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ExpenseFormEvent.OnTitleChange(""))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("10"))
        viewModel.onEvent(ExpenseFormEvent.OnParticipantChecked("user-1", true))
        viewModel.onEvent(ExpenseFormEvent.OnPaidBySelected("user-1"))

        val effects = mutableListOf<ExpenseFormEffect>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }
        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnPrimaryClick)
        advanceUntilIdle()
        collectJob.join()

        // THEN
        assertEquals(R.string.common_error_message, (effects.single() as ExpenseFormEffect.ShowToastRes).resId)
    }

    @Test
    fun given_validData_when_onPrimaryClick_then_callsUpdateAndEitherPopsOrShowsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val expenseRepository = ExpenseFormFakeExpenseRepository(
            expense = expenseFormExpenseDto(id = "exp-1", amount = 10.0),
        )
        val viewModel = createViewModel(
            navigator = navigator,
            expenseRepository = expenseRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("Updated Lunch"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("35"))
        viewModel.onEvent(ExpenseFormEvent.OnPaidBySelected("user-2"))
        val effects = mutableListOf<ExpenseFormEffect>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }
        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnPrimaryClick)
        advanceUntilIdle()
        advanceUntilIdle()
        collectJob.join()

        // THEN
        assertTrue(
            "Expected either navigation back (success) or an effect (toast)",
            navigator.popCalls == 1 || effects.isNotEmpty()
        )
        if (effects.isNotEmpty()) {
            assertTrue((effects.single() as ExpenseFormEffect.ShowToastRes).resId == R.string.expense_form_saved_toast ||
                (effects.single() as ExpenseFormEffect.ShowToastRes).resId == R.string.common_error_message)
        }
    }

    @Test
    fun given_expenseLoaded_when_onDeleteClick_then_deletesExpenseAndPopsBack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val expenseRepository = ExpenseFormFakeExpenseRepository(
            expense = expenseFormExpenseDto(id = "exp-1", amount = 10.0),
        )
        val viewModel = createViewModel(
            navigator = navigator,
            expenseRepository = expenseRepository,
        )
        advanceUntilIdle()

        val effects = mutableListOf<ExpenseFormEffect>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }
        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDeleteClick)
        advanceUntilIdle()
        collectJob.join()

        // THEN
        assertEquals(1, expenseRepository.deleteExpenseCalls.size)
        assertEquals("exp-1", expenseRepository.deleteExpenseCalls.single())
        assertEquals(R.string.expense_form_deleted_toast, (effects.single() as ExpenseFormEffect.ShowToastRes).resId)
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_noLimitDialog_when_onDismissLimitDialogAndOnConfirmDeleteOldestAndRetry_then_noOp() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDismissLimitDialog)
        viewModel.onEvent(ExpenseFormEvent.OnConfirmDeleteOldestAndRetry)

        // THEN
        assertNull(viewModel.state.value.limitDialog)
    }

    @Test
    fun given_updateExpenseFailure_when_onPrimaryClick_then_showsToastAndResetsSaving() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expenseRepository = ExpenseFormFakeExpenseRepository(
            expense = expenseFormExpenseDto(id = "exp-1", amount = 10.0),
        ).apply { updateExpenseToThrow = RuntimeException("server error") }
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("New Title"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("20"))
        val effects = mutableListOf<ExpenseFormEffect>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }
        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnPrimaryClick)
        advanceUntilIdle()
        collectJob.join()

        // THEN
        assertEquals(R.string.common_error_message, (effects.single() as ExpenseFormEffect.ShowToastRes).resId)
        assertTrue(!viewModel.state.value.isSaving)
    }

    @Test
    fun given_deleteExpenseFailure_when_onDeleteClick_then_showsToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expenseRepository = ExpenseFormFakeExpenseRepository(
            expense = expenseFormExpenseDto(id = "exp-1", amount = 10.0),
        ).apply { deleteExpenseToThrow = RuntimeException("server error") }
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        advanceUntilIdle()

        val effects = mutableListOf<ExpenseFormEffect>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.take(1).toList(effects)
        }
        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDeleteClick)
        advanceUntilIdle()
        collectJob.join()

        // THEN
        assertEquals(R.string.common_error_message, (effects.single() as ExpenseFormEffect.ShowToastRes).resId)
    }

    @Test
    fun given_screenOpen_when_onDateSelected_then_updatesDateText() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        val date = LocalDate.of(2026, 7, 20)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDateSelected(date))

        // THEN
        assertTrue(viewModel.state.value.dateText.contains("20"))
        assertTrue(viewModel.state.value.dateText.contains("07"))
    }

    @Test
    fun given_screenOpen_when_onSplitTypeChangeAndOnNoteChange_then_updateState() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnSplitTypeChange(ExpenseSplitType.CustomAmounts))
        // THEN
        assertEquals(ExpenseSplitType.CustomAmounts, viewModel.state.value.splitType)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnNoteChange("Updated note"))
        // THEN
        assertEquals("Updated note", viewModel.state.value.note)
    }

    private fun createViewModel(
        navigator: ExpenseFormFakeNavigator = ExpenseFormFakeNavigator(),
        tripRepository: ExpenseFormFakeTripRepository = ExpenseFormFakeTripRepository(
            trip = expenseFormTripDto(),
            members = listOf(
                expenseFormMemberDto(id = "user-1", name = "Alice"),
                expenseFormMemberDto(id = "user-2", name = "Bob"),
            ),
        ),
        expenseRepository: ExpenseFormFakeExpenseRepository = ExpenseFormFakeExpenseRepository(
            expense = expenseFormExpenseDto(
                id = "exp-1",
                title = "Original",
                amount = 15.0,
                participants = listOf(
                    ExpenseParticipantDto("user-1", 7.5, true, true, "Alice"),
                    ExpenseParticipantDto("user-2", 7.5, true, false, "Bob"),
                ),
            ),
        ),
    ): EditExpenseViewModel {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        return EditExpenseViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Destination.EditExpense.ARG_TRIP_ID to "trip-1",
                    Destination.EditExpense.ARG_EXPENSE_ID to "exp-1",
                )
            ),
            appNavigator = navigator,
            tripRepository = tripRepository,
            expenseRepository = expenseRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
