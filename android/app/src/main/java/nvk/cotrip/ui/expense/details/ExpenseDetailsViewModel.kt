package nvk.cotrip.ui.expense.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseParticipantDto
import nvk.cotrip.data.network.dto.ExpenseParticipantInput
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExpenseDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.ExpenseDetails.ARG_TRIP_ID])
    private val expenseId: String =
        checkNotNull(savedStateHandle[Destination.ExpenseDetails.ARG_EXPENSE_ID])

    private var currentExpense: ExpenseDto? = null
    private var membersById: Map<String, MemberDto> = emptyMap()
    private var meId: String? = null
    private var currencySymbol: String = "€"

    private val _state = MutableStateFlow(
        ExpenseDetailsState(
            tripId = tripId,
            expenseId = expenseId,
            isLoading = true,
            title = "",
            amount = "",
            status = ExpenseDetailsStatus.Planned,
            paidBy = null,
            date = null,
            splitType = "",
            note = null,
            splitRows = emptyList(),
            total = ""
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExpenseDetailsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeExpense()
        refreshExpense(showErrorToast = false)
    }

    fun onEvent(event: ExpenseDetailsEvent) {
        when (event) {
            ExpenseDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            ExpenseDetailsEvent.OnRefresh -> refreshExpense(showErrorToast = true)
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

    private fun observeExpense() {
        viewModelScope.launch {
            val expenseFlow = expenseRepository.getExpense(expenseId)
            val tripFlow = tripRepository.getTrip(tripId)
            val membersFlow = tripRepository.tripMembers(tripId)
            combine(
                expenseFlow,
                tripFlow,
                membersFlow,
                userRepository.me,
            ) { expense, trip, members, me ->
                ExpensePayload(
                    expense = expense,
                    trip = trip,
                    members = members,
                    meId = me?.id,
                )
            }.collect { payload ->
                currentExpense = payload.expense
                currencySymbol = currencySymbolFor(payload.trip.currencyCode)
                membersById = payload.members.associateBy { it.userId }
                meId = payload.meId
                _state.update {
                    payload.expense.toState(
                        meId = payload.meId.orEmpty(),
                        membersById = membersById,
                        currencySymbol = currencySymbol,
                    ).copy(isLoading = false)
                }
            }
        }
    }

    private fun refreshExpense(showErrorToast: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.refreshTrips().getOrThrow()
                    tripRepository.tripMembers(tripId).first()
                    expenseRepository.refreshExpenses(tripId).getOrThrow()
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false) }
                    if (showErrorToast) {
                        _effects.emit(
                            ExpenseDetailsEffect.ShowToastRes(
                                uiErrorMapper.messageRes(
                                    result
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    private fun markAsPaid() {
        val expense = currentExpense ?: return
        if (expense.status != "planned") return
        val payerId = meId ?: return
        val participants = expense.participants.map { participant ->
            participant.copy(isPaid = participant.userId == payerId)
        }
        updateExpense(
            ExpenseUpdateRequest(
                status = "paid",
                paidById = payerId,
                date = LocalDate.now().toString(),
                participants = participants.toInputs()
            )
        )
    }

    private fun markAllSettled() {
        val expense = currentExpense ?: return
        if (expense.status != "paid") return
        val participants = expense.participants.map { it.copy(isPaid = true) }
        updateExpense(
            ExpenseUpdateRequest(
                participants = participants.toInputs()
            )
        )
    }

    private fun markParticipantPaid(participantId: String) {
        val expense = currentExpense ?: return
        if (expense.status != "paid") return
        val participants = expense.participants.map { participant ->
            if (participant.userId == participantId) participant.copy(isPaid = true) else participant
        }
        updateExpense(
            ExpenseUpdateRequest(
                participants = participants.toInputs()
            )
        )
    }

    private fun updateExpense(request: ExpenseUpdateRequest) {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    expenseRepository.updateExpense(expenseId, request)
                }
            }) {
                is ApiResult.Success -> refreshExpense(showErrorToast = false)
                is ApiResult.Failure -> {
                    _effects.emit(ExpenseDetailsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private data class ExpensePayload(
        val expense: ExpenseDto,
        val trip: TripDto,
        val members: List<MemberDto>,
        val meId: String?,
    )
}

private fun ExpenseDto.toState(
    meId: String,
    membersById: Map<String, MemberDto>,
    currencySymbol: String,
): ExpenseDetailsState {
    val statusUi = when {
        status == "planned" -> ExpenseDetailsStatus.Planned
        participants.filter { it.isIncluded }.all { it.isPaid } -> ExpenseDetailsStatus.Settled
        else -> ExpenseDetailsStatus.Unsettled
    }

    val paidByName = paidById?.let { payerId ->
        if (payerId == meId) "You" else membersById[payerId]?.name
    }

    val splitTypeLabel = if (splitType == "equally") "Split equally" else "Custom amounts"
    val shares = computeShares(this)
    val splitRows = participants.filter { it.isIncluded }.map { participant ->
        val member = membersById[participant.userId]
        ExpenseSplitRowUi(
            id = participant.userId,
            initials = member?.initials ?: "?",
            name = member?.name ?: "Unknown",
            amount = formatMoney(shares[participant.userId] ?: 0.0, currencySymbol),
            isPaid = participant.isPaid
        )
    }

    val dateText = date?.let { LocalDate.parse(it) }
        ?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

    return ExpenseDetailsState(
        tripId = tripId,
        expenseId = id,
        isLoading = false,
        title = title,
        amount = formatMoney(amount, currencySymbol),
        status = statusUi,
        paidBy = paidByName,
        date = dateText,
        splitType = splitTypeLabel,
        note = note,
        splitRows = splitRows,
        total = formatMoney(amount, currencySymbol)
    )
}

private fun List<ExpenseParticipantDto>.toInputs(): List<ExpenseParticipantInput> {
    return map { participant ->
        ExpenseParticipantInput(
            userId = participant.userId,
            shareAmount = participant.shareAmount,
            isIncluded = participant.isIncluded,
            isPaid = participant.isPaid
        )
    }
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
