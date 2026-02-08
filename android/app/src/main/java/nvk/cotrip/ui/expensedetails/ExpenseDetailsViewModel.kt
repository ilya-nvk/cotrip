package nvk.cotrip.ui.expensedetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class ExpenseDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.ExpenseDetails.ARG_TRIP_ID])
    private val expenseId: String =
        checkNotNull(savedStateHandle[Destination.ExpenseDetails.ARG_EXPENSE_ID])

    private val _state = MutableStateFlow(initialState(tripId, expenseId))
    val state = _state.asStateFlow()

    fun onEvent(event: ExpenseDetailsEvent) {
        when (event) {
            ExpenseDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            ExpenseDetailsEvent.OnEditClick -> appNavigator.navigate(
                Destination.EditExpense(
                    tripId = tripId,
                    expenseId = expenseId
                )
            )

            ExpenseDetailsEvent.OnMarkAsPaidClick -> markAsPaid()
            ExpenseDetailsEvent.OnMarkAllSettledClick -> markAllSettled()
            is ExpenseDetailsEvent.OnMarkParticipantPaidClick -> markParticipantPaid(event.participantId)
        }
    }

    private fun markAsPaid() {
        _state.update { current ->
            if (current.status != ExpenseDetailsStatus.Planned) return@update current
            current.copy(
                status = ExpenseDetailsStatus.Unsettled,
                paidBy = "You",
                date = "Jul 16, 2026"
            )
        }
    }

    private fun markAllSettled() {
        _state.update { current ->
            val settledRows = current.splitRows.map { it.copy(isPaid = true) }
            current.copy(
                status = ExpenseDetailsStatus.Settled,
                splitRows = settledRows
            )
        }
    }

    private fun markParticipantPaid(participantId: String) {
        _state.update { current ->
            if (current.status == ExpenseDetailsStatus.Planned) return@update current
            val updatedRows = current.splitRows.map { row ->
                if (row.id == participantId) row.copy(isPaid = true) else row
            }
            val allSettled = updatedRows.all { it.isPaid }
            current.copy(
                status = if (allSettled) ExpenseDetailsStatus.Settled else current.status,
                splitRows = updatedRows
            )
        }
    }

    private fun initialState(tripId: String, expenseId: String): ExpenseDetailsState {
        return if (expenseId == "ex4" || expenseId == "ex5") {
            ExpenseDetailsState(
                tripId = tripId,
                expenseId = expenseId,
                title = if (expenseId == "ex4") "Eiffel Tower tickets" else "Seine River cruise",
                amount = if (expenseId == "ex4") "€60.00" else "€120.00",
                status = ExpenseDetailsStatus.Planned,
                paidBy = null,
                date = null,
                splitType = "Split equally",
                note = null,
                splitRows = listOf(
                    ExpenseSplitRowUi(
                        "p1",
                        "YO",
                        "You",
                        if (expenseId == "ex4") "€15.00" else "€30.00",
                        false
                    ),
                    ExpenseSplitRowUi(
                        "p2",
                        "SL",
                        "Sophie Laurent",
                        if (expenseId == "ex4") "€15.00" else "€30.00",
                        false
                    ),
                    ExpenseSplitRowUi(
                        "p3",
                        "JC",
                        "James Chen",
                        if (expenseId == "ex4") "€15.00" else "€30.00",
                        false
                    ),
                    ExpenseSplitRowUi(
                        "p4",
                        "MG",
                        "Maria Garcia",
                        if (expenseId == "ex4") "€15.00" else "€30.00",
                        false
                    ),
                ),
                total = if (expenseId == "ex4") "€60.00" else "€120.00"
            )
        } else {
            ExpenseDetailsState(
                tripId = tripId,
                expenseId = expenseId,
                title = "Louvre Museum tickets",
                amount = "€68.00",
                status = ExpenseDetailsStatus.Unsettled,
                paidBy = "You",
                date = "Jul 16, 2026",
                splitType = "Split equally",
                note = "Bought online in advance",
                splitRows = listOf(
                    ExpenseSplitRowUi("p1", "YO", "You", "€17.00", true),
                    ExpenseSplitRowUi("p2", "SL", "Sophie Laurent", "€17.00", false),
                    ExpenseSplitRowUi("p3", "JC", "James Chen", "€17.00", false),
                    ExpenseSplitRowUi("p4", "MG", "Maria Garcia", "€17.00", true),
                ),
                total = "€68.00"
            )
        }
    }
}
