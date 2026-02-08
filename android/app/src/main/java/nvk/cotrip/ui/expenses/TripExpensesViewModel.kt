package nvk.cotrip.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class TripExpensesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.Expenses.ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        TripExpensesState(
            tripId = tripId,
            summary = ExpenseSummaryUi(
                totalSpent = "€185.00",
                balanceLabel = "Owed",
                balanceAmount = "€29.75",
                totalPlanned = "€180.00"
            ),
            spent = listOf(
                ExpenseListItemUi(
                    id = "ex1",
                    title = "Louvre Museum tickets",
                    amount = "€68.00",
                    paidBy = "Paid by You",
                    splitType = "Split equally",
                    settlement = ExpenseSettlementUi.OwedToYou("€51.00")
                ),
                ExpenseListItemUi(
                    id = "ex2",
                    title = "Lunch at Le Marais",
                    amount = "€85.00",
                    paidBy = "Paid by Sophie Laurent",
                    splitType = "Split equally",
                    settlement = ExpenseSettlementUi.YouOwe("€21.25")
                ),
                ExpenseListItemUi(
                    id = "ex3",
                    title = "Taxi to hotel",
                    amount = "€32.00",
                    paidBy = "Paid by You",
                    splitType = "Split equally",
                    settlement = ExpenseSettlementUi.Settled
                ),
            ),
            planned = listOf(
                ExpenseListItemUi(
                    id = "ex4",
                    title = "Eiffel Tower tickets",
                    amount = "€60.00",
                    paidBy = "Planned",
                    splitType = "Split equally",
                    settlement = ExpenseSettlementUi.Planned
                ),
                ExpenseListItemUi(
                    id = "ex5",
                    title = "Seine River cruise",
                    amount = "€120.00",
                    paidBy = "Planned",
                    splitType = "Split equally",
                    settlement = ExpenseSettlementUi.Planned
                ),
            )
        )
    )
    val state = _state.asStateFlow()

    fun onEvent(event: TripExpensesEvent) {
        when (event) {
            TripExpensesEvent.OnBackClick -> appNavigator.popBackStack()
            TripExpensesEvent.OnAddExpenseClick -> appNavigator.navigate(
                Destination.CreateExpense(
                    tripId
                )
            )

            is TripExpensesEvent.OnExpenseClick -> appNavigator.navigate(
                Destination.ExpenseDetails(
                    tripId = tripId,
                    expenseId = event.expenseId
                )
            )
        }
    }
}
