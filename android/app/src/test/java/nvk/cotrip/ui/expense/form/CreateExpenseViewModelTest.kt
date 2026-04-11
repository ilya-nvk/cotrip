package nvk.cotrip.ui.expense.form

import android.app.Application
import androidx.lifecycle.SavedStateHandle
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
import nvk.cotrip.testing.MainDispatcherRule
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.Destination
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CreateExpenseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val networkStateProvider = mockk<NetworkStateProvider>()
    private val apiCaller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun given_loadMembersSuccess_when_init_then_populatesStateWithParticipantsAndDate() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val tripRepository = ExpenseFormFakeTripRepository(
            trip = expenseFormTripDto(id = "trip-1", currencyCode = "EUR"),
            members = listOf(
                expenseFormMemberDto(id = "user-1", initials = "U1", name = "Alice"),
                expenseFormMemberDto(id = "user-2", initials = "U2", name = "Bob"),
            ),
        )
        val userRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1", name = "Alice"))
        val viewModel = createViewModel(
            tripRepository = tripRepository,
            expenseRepository = ExpenseFormFakeExpenseRepository(),
            userRepository = userRepository,
        )

        // WHEN
        advanceUntilIdle()

        // THEN
        val state = viewModel.state.value
        assertEquals(2, state.participants.size)
        assertEquals("€", state.currencySymbol)
        assertEquals("user-1", state.paidById)
        assertTrue(state.dateText.isNotBlank())
        assertTrue(state.participants.all { it.isSelected })
    }

    @Test
    fun given_loadMembersFailure_when_init_then_stateKeepsEmptyParticipants() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val tripRepository = ExpenseFormFakeTripRepository(
            trip = expenseFormTripDto(),
            members = listOf(expenseFormMemberDto(id = "user-1")),
        ).apply { getTripError = RuntimeException("load failed") }
        val viewModel = createViewModel(
            tripRepository = tripRepository,
            expenseRepository = ExpenseFormFakeExpenseRepository(),
            userRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
        )
        // WHEN
        advanceUntilIdle()

        // THEN
        assertEquals(0, viewModel.state.value.participants.size)
    }

    @Test
    fun given_screenOpen_when_onBackClick_then_popsBackStack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(navigator = navigator)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnBackClick)

        // THEN
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_blankTitle_when_onPrimaryClick_then_emitsErrorToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
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
    fun given_invalidAmount_when_onPrimaryClick_then_emitsErrorToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("Lunch"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange(""))
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
    fun given_noParticipantsSelected_when_onPrimaryClick_then_emitsErrorToast() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("Lunch"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("20"))
        viewModel.onEvent(ExpenseFormEvent.OnParticipantChecked("user-1", false))
        viewModel.onEvent(ExpenseFormEvent.OnParticipantChecked("user-2", false))
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
    fun given_validData_when_onPrimaryClick_then_createsExpenseAndPopsBack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val navigator = ExpenseFormFakeNavigator()
        val expenseRepository = ExpenseFormFakeExpenseRepository()
        val viewModel = createViewModel(
            navigator = navigator,
            expenseRepository = expenseRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("Lunch"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("25.50"))
        viewModel.onEvent(ExpenseFormEvent.OnPaidBySelected("user-1"))
        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnPrimaryClick)
        advanceUntilIdle()

        // THEN
        assertEquals(1, expenseRepository.createExpenseCalls.size)
        assertEquals("trip-1", expenseRepository.createExpenseCalls.single().first)
        assertEquals("Lunch", expenseRepository.createExpenseCalls.single().second.title.trim())
        assertEquals(25.50, expenseRepository.createExpenseCalls.single().second.amount, 0.001)
        assertEquals(1, navigator.popCalls)
    }

    @Test
    fun given_createScreen_when_onDeleteClick_then_doesNotEmitEffect() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDeleteClick)
        advanceUntilIdle()

        // THEN
        assertTrue(!viewModel.state.value.isSaving)
    }

    @Test
    fun given_limitDialogShown_when_onDismissLimitDialog_then_clearsLimitDialog() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDismissLimitDialog)

        // THEN
        assertNull(viewModel.state.value.limitDialog)
    }

    @Test
    fun given_screenOpen_when_onDateSelected_then_updatesDateText() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        val date = LocalDate.of(2026, 6, 15)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDateSelected(date))

        // THEN
        assertTrue(viewModel.state.value.dateText.contains("15"))
        assertTrue(viewModel.state.value.dateText.contains("06"))
        assertTrue(viewModel.state.value.dateText.contains("2026"))
    }

    @Test
    fun given_screenOpen_when_onPaidByClickAndOnDismissPaidByPicker_then_togglePickerVisibility() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnPaidByClick)
        // THEN
        assertTrue(viewModel.state.value.paidByPickerVisible)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnDismissPaidByPicker)
        // THEN
        assertTrue(!viewModel.state.value.paidByPickerVisible)
    }

    @Test
    fun given_screenOpen_when_onTitleChange_then_updatesTitleAndRespectsLimit() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("A".repeat(200)))
        // THEN
        assertEquals(120, viewModel.state.value.title.length)
    }

    @Test
    fun given_screenOpen_when_onAmountChange_then_filtersNonNumeric() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("12abc34.56"))
        // THEN
        assertEquals("1234.56", viewModel.state.value.amount)
    }

    @Test
    fun given_screenOpen_when_onStatusChange_then_updatesStatusAndPaidBy() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnStatusChange(ExpenseFormStatus.Planned))
        // THEN
        assertEquals(ExpenseFormStatus.Planned, viewModel.state.value.status)
        assertNull(viewModel.state.value.paidById)
        assertEquals("", viewModel.state.value.dateText)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnStatusChange(ExpenseFormStatus.Paid))
        // THEN
        assertEquals(ExpenseFormStatus.Paid, viewModel.state.value.status)
        assertEquals("user-1", viewModel.state.value.paidById)
    }

    @Test
    fun given_paidByPickerOpen_when_onPaidBySelected_then_setsPaidByAndClosesPicker() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ExpenseFormEvent.OnPaidByClick)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnPaidBySelected("user-2"))

        // THEN
        assertEquals("user-2", viewModel.state.value.paidById)
        assertTrue(!viewModel.state.value.paidByPickerVisible)
    }

    @Test
    fun given_screenOpen_when_onParticipantChecked_then_togglesSelection() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnParticipantChecked("user-2", false))
        // THEN
        assertTrue(!viewModel.state.value.participants.single { it.id == "user-2" }.isSelected)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnParticipantChecked("user-2", true))
        // THEN
        assertTrue(viewModel.state.value.participants.single { it.id == "user-2" }.isSelected)
    }

    @Test
    fun given_screenOpen_when_onSplitTypeChange_then_updatesSplitType() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnSplitTypeChange(ExpenseSplitType.CustomAmounts))
        // THEN
        assertEquals(ExpenseSplitType.CustomAmounts, viewModel.state.value.splitType)

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnSplitTypeChange(ExpenseSplitType.SplitEqually))
        // THEN
        assertEquals(ExpenseSplitType.SplitEqually, viewModel.state.value.splitType)
    }

    @Test
    fun given_customSplit_when_onCustomAmountChange_then_updatesParticipantCustomAmount() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ExpenseFormEvent.OnSplitTypeChange(ExpenseSplitType.CustomAmounts))

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnCustomAmountChange("user-1", "10.5"))

        // THEN
        assertEquals("10.5", viewModel.state.value.participants.single { it.id == "user-1" }.customAmount)
    }

    @Test
    fun given_screenOpen_when_onNoteChange_then_updatesNoteAndRespectsLimit() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnNoteChange("Split for dinner"))
        // THEN
        assertEquals("Split for dinner", viewModel.state.value.note)
    }

    @Test
    fun given_createExpenseFailureWithLimitReached_when_onPrimaryClick_then_showsLimitDialog() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val limitReachedJson = """
            {"error":{"code":"limit_reached","message":"Limit","details":{"entity":"expense","scopeId":"trip-1","limit":100,"currentCount":100,"oldestCandidate":{"id":"exp-old","label":"Old expense","deletable":true}}}}
        """.trimIndent()
        val response = Response.error<String>(
            429,
            limitReachedJson.toResponseBody("application/json".toMediaType()),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository().apply {
            createExpenseToThrow = HttpException(response)
        }
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        advanceUntilIdle()

        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("New"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("10"))
        viewModel.onEvent(ExpenseFormEvent.OnPaidBySelected("user-1"))
        viewModel.onEvent(ExpenseFormEvent.OnPrimaryClick)
        advanceUntilIdle()

        // THEN
        val dialog = viewModel.state.value.limitDialog
        assertTrue(dialog != null)
        assertEquals("exp-old", dialog!!.oldestId)
        assertTrue(dialog.oldestLabel!!.contains("Old expense"))
        assertTrue(!viewModel.state.value.isSaving)
    }

    @Test
    fun given_createExpenseFailureWithoutLimit_when_onPrimaryClick_then_showsToastAndResetsSaving() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val expenseRepository = ExpenseFormFakeExpenseRepository().apply {
            createExpenseToThrow = RuntimeException("server error")
        }
        val viewModel = createViewModel(expenseRepository = expenseRepository)
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("New"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("10"))
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
        assertTrue(!viewModel.state.value.isSaving)
    }

    @Test
    fun given_limitDialogShown_when_deleteOldestAndRetrySuccess_then_popsBack() = runTest {
        // GIVEN
        every { networkStateProvider.isOnline() } returns true
        val limitReachedJson = """
            {"error":{"code":"limit_reached","message":"Limit","details":{"entity":"expense","scopeId":"trip-1","limit":100,"currentCount":100,"oldestCandidate":{"id":"exp-old","label":"Old","deletable":true}}}}
        """.trimIndent()
        val response = Response.error<String>(
            429,
            limitReachedJson.toResponseBody("application/json".toMediaType()),
        )
        val expenseRepository = ExpenseFormFakeExpenseRepository().apply {
            createExpenseToThrow = HttpException(response)
        }
        val navigator = ExpenseFormFakeNavigator()
        val viewModel = createViewModel(
            navigator = navigator,
            expenseRepository = expenseRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.OnTitleChange("New"))
        viewModel.onEvent(ExpenseFormEvent.OnAmountChange("10"))
        viewModel.onEvent(ExpenseFormEvent.OnPaidBySelected("user-1"))
        viewModel.onEvent(ExpenseFormEvent.OnPrimaryClick)
        advanceUntilIdle()

        expenseRepository.createExpenseToThrow = null
        // WHEN
        viewModel.onEvent(ExpenseFormEvent.OnConfirmDeleteOldestAndRetry)
        advanceUntilIdle()

        // THEN
        assertEquals(1, expenseRepository.deleteExpenseCalls.size)
        assertEquals("exp-old", expenseRepository.deleteExpenseCalls.single())
        assertEquals(1, navigator.popCalls)
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
        expenseRepository: ExpenseFormFakeExpenseRepository = ExpenseFormFakeExpenseRepository(),
        userRepository: ExpenseFormFakeUserRepository = ExpenseFormFakeUserRepository(initialMe = expenseFormUserDto(id = "user-1")),
    ): CreateExpenseViewModel {
        return CreateExpenseViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(Destination.CreateExpense.ARG_TRIP_ID to "trip-1")
            ),
            appNavigator = navigator,
            tripRepository = tripRepository,
            expenseRepository = expenseRepository,
            userRepository = userRepository,
            apiCaller = apiCaller,
            uiErrorMapper = UiErrorMapper(networkStateProvider),
        )
    }
}
