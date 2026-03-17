package nvk.cotrip.ui.expense.details

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
    @ApplicationContext private val appContext: Context,
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

    private val _state = MutableStateFlow<ExpenseDetailsState>(ExpenseDetailsState.Loading)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExpenseDetailsEffect>(extraBufferCapacity = 8)
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
            is ExpenseDetailsEvent.OnMarkParticipantPaidClick -> markParticipantPaid(event.participantId)
            is ExpenseDetailsEvent.OnUnmarkParticipantPaidClick ->
                unmarkParticipantPaid(event.participantId)
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
                _state.value = payload.expense.toState(
                    meId = payload.meId.orEmpty(),
                    membersById = membersById,
                    currencySymbol = currencySymbol,
                    context = appContext,
                )
            }
        }
    }

    private fun refreshExpense(showErrorToast: Boolean) {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                tripRepository.refreshTrips().getOrThrow()
                tripRepository.tripMembers(tripId).first()
                expenseRepository.refreshExpenses(tripId).getOrThrow()
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
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

    private fun markParticipantPaid(participantId: String) {
        val expense = currentExpense ?: return
        if (expense.status != "paid") return
        val currentUserId = meId ?: return
        if (participantId != currentUserId) return
        val participants = expense.participants.map { participant ->
            if (participant.userId == participantId) participant.copy(isPaid = true) else participant
        }
        updateExpense(
            ExpenseUpdateRequest(
                participants = participants.toInputs()
            )
        )
    }

    private fun unmarkParticipantPaid(participantId: String) {
        val expense = currentExpense ?: return
        if (expense.status != "paid") return
        val currentUserId = meId ?: return
        if (participantId != currentUserId) return
        val participants = expense.participants.map { participant ->
            if (participant.userId == participantId) participant.copy(isPaid = false) else participant
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
                expenseRepository.updateExpense(expenseId, request)
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
    context: Context,
): ExpenseDetailsState.Content {
    val statusUi = when {
        status == "planned" -> ExpenseDetailsStatus.Planned
        participants.filter { it.isIncluded }.all { it.isPaid } -> ExpenseDetailsStatus.Settled
        else -> ExpenseDetailsStatus.Unsettled
    }

    val paidByName = paidById?.let { payerId ->
        if (payerId == meId) {
            context.getString(R.string.common_you)
        } else {
            membersById[payerId]?.name
                ?: participants.firstOrNull { it.userId == payerId }?.name
        }
    }

    val splitTypeLabel = if (splitType == "equally") {
        context.getString(R.string.expense_form_split_equally)
    } else {
        context.getString(R.string.expense_form_custom_amounts)
    }
    val shares = computeShares(this)
    val splitRows = participants.filter { it.isIncluded }.map { participant ->
        val member = membersById[participant.userId]
        val participantName = member?.name
            ?: participant.name
            ?: context.getString(R.string.common_unknown)
        val participantInitials = member?.initials
            ?: participant.name?.let(::initialsFromName)
            ?: "?"
        ExpenseSplitRowUi(
            id = participant.userId,
            initials = participantInitials,
            name = participantName,
            photoUrl = member?.photoUrl,
            amount = formatMoney(shares[participant.userId] ?: 0.0, currencySymbol),
            isPaid = participant.isPaid,
            canTogglePaid = participant.userId == meId,
        )
    }

    val dateText = date?.let { LocalDate.parse(it) }
        ?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

    return ExpenseDetailsState.Content(
        tripId = tripId,
        expenseId = id,
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

private fun initialsFromName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> ("${parts[0].first()}${parts[1].first()}").uppercase(Locale.getDefault())
    }
}
