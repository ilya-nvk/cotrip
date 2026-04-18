package nvk.cotrip.ui.expense.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class TripExpensesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.Expenses.ARG_TRIP_ID])

    private val _state = MutableStateFlow<TripExpensesState>(TripExpensesState.Loading)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripExpensesEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private val isRefreshing = MutableStateFlow(false)

    init {
        observeData()
        refreshExpenses(isUserRefresh = false)
    }

    fun onEvent(event: TripExpensesEvent) {
        when (event) {
            TripExpensesEvent.OnBackClick -> appNavigator.popBackStack()
            TripExpensesEvent.OnAutoRefresh -> refreshExpenses(isUserRefresh = false)
            TripExpensesEvent.OnUserRefresh -> refreshExpenses(isUserRefresh = true)
            TripExpensesEvent.OnAddExpenseClick -> appNavigator.navigate(
                Destination.CreateExpense(tripId)
            )

            is TripExpensesEvent.OnExpenseClick -> appNavigator.navigate(
                Destination.ExpenseDetails(
                    tripId = tripId,
                    expenseId = event.expenseId
                )
            )
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                expenseRepository.observeExpenses(tripId),
                tripRepository.getTrip(tripId),
                tripRepository.tripMembers(tripId),
                userRepository.me,
                isRefreshing,
            ) { expenses, trip, members, me, refreshing ->
                ExpensesPayload(
                    trip = trip,
                    expenses = expenses,
                    members = members,
                    meId = me?.id,
                    refreshing = refreshing,
                )
            }.collect { payload ->
                val meId = payload.meId
                if (meId == null) {
                    _state.value = TripExpensesState.Loading
                    return@collect
                }
                val currencySymbol = currencySymbolFor(payload.trip.currencyCode)
                val memberById = payload.members.associateBy { it.userId }
                var totalSpent = 0.0
                var totalPlanned = 0.0
                var netBalance = 0.0

                val spentItems = mutableListOf<ExpenseListItemUi>()
                val plannedItems = mutableListOf<ExpenseListItemUi>()

                payload.expenses.forEach { expense ->
                    val shares = computeShares(expense)
                    val myShare = shares[meId] ?: 0.0

                    if (expense.status == "paid") {
                        totalSpent += expense.amount
                        if (expense.paidById == meId) {
                            netBalance += (expense.amount - myShare)
                        } else {
                            netBalance -= myShare
                        }
                    } else {
                        totalPlanned += expense.amount
                    }

                    val listItem = expense.toListItem(
                        currencySymbol = currencySymbol,
                        memberById = memberById,
                        meId = meId,
                        shares = shares,
                        context = appContext,
                    )
                    if (expense.status == "paid") {
                        spentItems.add(listItem)
                    } else {
                        plannedItems.add(listItem)
                    }
                }

                val balanceLabel = when {
                    abs(netBalance) < 0.01 -> appContext.getString(R.string.trip_expenses_balance_settled)
                    netBalance > 0 -> appContext.getString(R.string.trip_expenses_balance_owed)
                    else -> appContext.getString(R.string.trip_expenses_balance_you_owe)
                }

                _state.value = TripExpensesState.Content(
                    tripId = tripId,
                    summary = ExpenseSummaryUi(
                        totalSpent = formatMoney(totalSpent, currencySymbol),
                        balanceLabel = balanceLabel,
                        balanceAmount = formatMoney(abs(netBalance), currencySymbol),
                        totalPlanned = formatMoney(totalPlanned, currencySymbol)
                    ),
                    spent = spentItems,
                    planned = plannedItems,
                    isRefreshing = payload.refreshing,
                )
            }
        }
    }

    private fun refreshExpenses(isUserRefresh: Boolean) {
        viewModelScope.launch {
            if (isUserRefresh) {
                val current = _state.value as? TripExpensesState.Content
                if (current != null) {
                    _state.value = current.copy(isRefreshing = true)
                }
                isRefreshing.value = true
            }
            when (val result = apiCaller.call {
                tripRepository.getTrip(tripId).first()
                expenseRepository.refreshExpenses(tripId).getOrThrow()
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    if (isUserRefresh) {
                        _effects.emit(TripExpensesEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    }
                }
            }
            isRefreshing.value = false
        }
    }

    private data class ExpensesPayload(
        val trip: TripDto,
        val expenses: List<ExpenseDto>,
        val members: List<MemberDto>,
        val meId: String?,
        val refreshing: Boolean,
    )
}

private fun ExpenseDto.toListItem(
    currencySymbol: String,
    memberById: Map<String, MemberDto>,
    meId: String,
    shares: Map<String, Double>,
    context: Context,
): ExpenseListItemUi {
    val paidBySnapshotName = paidById?.let { payerId ->
        participants.firstOrNull { it.userId == payerId }?.name
    }
    val paidByText = when {
        status != "paid" -> context.getString(R.string.trip_expenses_paid_status_planned)
        paidById == null -> context.getString(R.string.trip_expenses_paid_status_paid)
        paidById == meId -> context.getString(R.string.trip_expenses_paid_by_you)
        else -> context.getString(
            R.string.trip_expenses_paid_by_member,
            memberById[paidById]?.name
                ?: paidBySnapshotName
                ?: context.getString(R.string.common_member)
        )
    }
    val splitTypeText = if (splitType == "equally") {
        context.getString(R.string.expense_form_split_equally)
    } else {
        context.getString(R.string.expense_form_custom_amounts)
    }
    val settlement = when {
        status != "paid" -> ExpenseSettlementUi.Planned
        paidById == meId -> {
            val owed = participants.filter { it.isIncluded && it.userId != meId && !it.isPaid }
                .sumOf { shares[it.userId] ?: 0.0 }
            if (owed <= 0.01) ExpenseSettlementUi.Settled
            else ExpenseSettlementUi.OwedToYou(formatMoney(owed, currencySymbol))
        }

        else -> {
            val meParticipant = participants.firstOrNull { it.userId == meId }
            val meShare = shares[meId] ?: 0.0
            if (meParticipant == null || meParticipant.isPaid || meShare <= 0.0) {
                ExpenseSettlementUi.Settled
            } else {
                ExpenseSettlementUi.YouOwe(formatMoney(meShare, currencySymbol))
            }
        }
    }

    return ExpenseListItemUi(
        id = id,
        title = title,
        amount = formatMoney(amount, currencySymbol),
        paidBy = paidByText,
        splitType = splitTypeText,
        settlement = settlement
    )
}

private fun computeShares(expense: ExpenseDto): Map<String, Double> {
    val included = expense.participants.filter { it.isIncluded }
    if (included.isEmpty()) return emptyMap()
    return if (expense.splitType == "equally") {
        val share = expense.amount / included.size
        included.associate { it.userId to share }
    } else {
        included.associate { it.userId to (it.shareAmount ?: 0.0) }
    }
}

private fun formatMoney(amount: Double, currencySymbol: String): String {
    val display = if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(appUiLocale(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}
