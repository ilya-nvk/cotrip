package nvk.cotrip.ui.expenseform

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ExpenseFormContract {
    val state: StateFlow<ExpenseFormState>
    val effects: SharedFlow<ExpenseFormEffect>
    fun onEvent(event: ExpenseFormEvent)
}
