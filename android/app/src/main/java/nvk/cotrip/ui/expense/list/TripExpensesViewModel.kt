package nvk.cotrip.ui.expense.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class TripExpensesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.Expenses.ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        TripExpensesState(
            tripId = tripId,
            summary = ExpenseSummaryUi(
                totalSpent = "€0",
                balanceLabel = "Settled",
                balanceAmount = "€0",
                totalPlanned = "€0"
            ),
            spent = emptyList(),
            planned = emptyList(),
            isRefreshing = false,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripExpensesEffect>()
    val effects = _effects.asSharedFlow()

    private val membersState = MutableStateFlow<List<MemberDto>>(emptyList())
    private val meIdState = MutableStateFlow<String?>(null)

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
                membersState,
                meIdState
            ) { expenses, trip, members, meId ->
                if (meId == null) {
                    null
                } else {
                    ExpensesPayload(
                        trip = trip,
                        expenses = expenses,
                        members = members,
                        meId = meId
                    )
                }
            }.collect { payload ->
                if (payload == null) return@collect
                val currencySymbol = currencySymbolFor(payload.trip.currencyCode)
                val memberById = payload.members.associateBy { it.userId }
                var totalSpent = 0.0
                var totalPlanned = 0.0
                var netBalance = 0.0

                val spentItems = mutableListOf<ExpenseListItemUi>()
                val plannedItems = mutableListOf<ExpenseListItemUi>()

                payload.expenses.forEach { expense ->
                    val shares = computeShares(expense)
                    val myShare = shares[payload.meId] ?: 0.0

                    if (expense.status == "paid") {
                        totalSpent += expense.amount
                        if (expense.paidById == payload.meId) {
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
                        meId = payload.meId,
                        shares = shares
                    )
                    if (expense.status == "paid") {
                        spentItems.add(listItem)
                    } else {
                        plannedItems.add(listItem)
                    }
                }

                val balanceLabel = when {
                    abs(netBalance) < 0.01 -> "Settled"
                    netBalance > 0 -> "Owed"
                    else -> "You owe"
                }

                _state.update {
                    it.copy(
                        summary = ExpenseSummaryUi(
                            totalSpent = formatMoney(totalSpent, currencySymbol),
                            balanceLabel = balanceLabel,
                            balanceAmount = formatMoney(abs(netBalance), currencySymbol),
                            totalPlanned = formatMoney(totalPlanned, currencySymbol)
                        ),
                        spent = spentItems,
                        planned = plannedItems
                    )
                }
            }
        }
    }

    private fun refreshExpenses(isUserRefresh: Boolean) {
        viewModelScope.launch {
            if (isUserRefresh) {
                _state.update { it.copy(isRefreshing = true) }
            }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.getTrip(tripId).first()
                    expenseRepository.refreshExpenses(tripId).getOrThrow()
                    val members = tripRepository.tripMembers(tripId).first()
                    val me = checkNotNull(userRepository.me.first())
                    membersState.value = members
                    meIdState.value = me.id
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    _effects.emit(TripExpensesEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private data class ExpensesPayload(
        val trip: TripDto,
        val expenses: List<ExpenseDto>,
        val members: List<MemberDto>,
        val meId: String,
    )
}

private fun ExpenseDto.toListItem(
    currencySymbol: String,
    memberById: Map<String, MemberDto>,
    meId: String,
    shares: Map<String, Double>,
): ExpenseListItemUi {
    val paidByText = when {
        status != "paid" -> "Planned"
        paidById == null -> "Paid"
        paidById == meId -> "Paid by You"
        else -> "Paid by ${memberById[paidById]?.name ?: "Member"}"
    }
    val splitTypeText = if (splitType == "equally") "Split equally" else "Custom amounts"
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
        String.format(Locale.getDefault(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}
