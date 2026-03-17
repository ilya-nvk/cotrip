package nvk.cotrip.ui.expense.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseParticipantInput
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.TextInputLimits
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class EditExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel(), ExpenseFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.EditExpense.ARG_TRIP_ID])
    private val expenseId: String =
        checkNotNull(savedStateHandle[Destination.EditExpense.ARG_EXPENSE_ID])

    private var members: List<MemberDto> = emptyList()
    private var currencyCode: String = "EUR"
    private var selectedDate: LocalDate? = null

    private val _state = MutableStateFlow(
        ExpenseFormState(
            mode = ExpenseFormMode.Edit,
            expenseId = expenseId,
            title = "",
            amount = "",
            currencySymbol = "€",
            status = ExpenseFormStatus.Paid,
            paidById = null,
            dateText = "",
            participants = emptyList(),
            splitType = ExpenseSplitType.SplitEqually,
            note = "",
            isSaving = false,
            paidByPickerVisible = false
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExpenseFormEffect>(extraBufferCapacity = 8)
    override val effects = _effects.asSharedFlow()

    init {
        loadExpense()
    }

    override fun onEvent(event: ExpenseFormEvent) {
        when (event) {
            ExpenseFormEvent.OnBackClick -> appNavigator.popBackStack()
            ExpenseFormEvent.OnPrimaryClick -> updateExpense()
            ExpenseFormEvent.OnDeleteClick -> deleteExpense()
            ExpenseFormEvent.OnDismissLimitDialog,
            ExpenseFormEvent.OnConfirmDeleteOldestAndRetry -> Unit
            ExpenseFormEvent.OnDateClick -> Unit
            is ExpenseFormEvent.OnDateSelected -> selectDate(event.date)
            ExpenseFormEvent.OnPaidByClick -> _state.update { it.copy(paidByPickerVisible = true) }
            ExpenseFormEvent.OnDismissPaidByPicker -> _state.update { it.copy(paidByPickerVisible = false) }
            is ExpenseFormEvent.OnTitleChange -> _state.update {
                it.copy(title = event.value.take(TextInputLimits.EXPENSE_TITLE))
            }
            is ExpenseFormEvent.OnAmountChange -> _state.update { it.copy(amount = moneyInput(event.value)) }
            is ExpenseFormEvent.OnStatusChange -> updateStatus(event.value)
            is ExpenseFormEvent.OnPaidBySelected -> _state.update {
                it.copy(
                    paidById = event.participantId,
                    paidByPickerVisible = false
                )
            }

            is ExpenseFormEvent.OnParticipantChecked -> _state.update { current ->
                current.copy(
                    participants = current.participants.map { participant ->
                        if (participant.id == event.participantId) participant.copy(isSelected = event.checked)
                        else participant
                    }
                )
            }

            is ExpenseFormEvent.OnSplitTypeChange -> _state.update { it.copy(splitType = event.value) }
            is ExpenseFormEvent.OnCustomAmountChange -> _state.update { current ->
                current.copy(
                    participants = current.participants.map { participant ->
                        if (participant.id == event.participantId) participant.copy(
                            customAmount = moneyInput(
                                event.value,
                                TextInputLimits.EXPENSE_CUSTOM_AMOUNT
                            )
                        )
                        else participant
                    }
                )
            }

            is ExpenseFormEvent.OnNoteChange -> _state.update {
                it.copy(note = event.value.take(TextInputLimits.EXPENSE_NOTE))
            }
        }
    }

    private fun loadExpense() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                val expense = expenseRepository.getExpense(expenseId).first()
                val trip = tripRepository.getTrip(tripId).first()
                val members = tripRepository.tripMembers(tripId).first()
                ExpensePayload(
                    expense = expense,
                    members = members,
                    currencyCode = trip.currencyCode
                )
            }) {
                is ApiResult.Success -> {
                    val payload = result.data
                    members = payload.members
                    currencyCode = payload.currencyCode
                    val expense = payload.expense
                    selectedDate = expense.date?.let { LocalDate.parse(it) }
                    val participants = payload.members.map { member ->
                        val participant =
                            expense.participants.firstOrNull { it.userId == member.userId }
                        ExpenseParticipantUi(
                            id = member.userId,
                            initials = member.initials,
                            name = member.name,
                            photoUrl = member.photoUrl,
                            isSelected = participant?.isIncluded ?: false,
                            customAmount = participant?.shareAmount?.let { formatAmount(it) }
                                .orEmpty()
                        )
                    }
                    _state.update {
                        it.copy(
                            title = expense.title,
                            amount = formatAmount(expense.amount),
                            currencySymbol = currencySymbolFor(payload.currencyCode),
                            status = expense.status.toFormStatus(),
                            paidById = expense.paidById,
                            dateText = selectedDate?.let { date -> formatDate(date) }.orEmpty(),
                            participants = participants,
                            splitType = expense.splitType.toFormSplitType(),
                            note = expense.note.orEmpty()
                        )
                    }
                }

                is ApiResult.Failure -> {
                    emit(ExpenseFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun updateStatus(status: ExpenseFormStatus) {
        _state.update { current ->
            val paidById = if (status == ExpenseFormStatus.Planned) null else current.paidById
            current.copy(
                status = status,
                paidById = paidById,
                dateText = if (status == ExpenseFormStatus.Planned) "" else current.dateText
            )
        }
    }

    private fun selectDate(date: LocalDate) {
        selectedDate = date
        _state.update { it.copy(dateText = formatDate(date)) }
    }

    private fun updateExpense() {
        val snapshot = _state.value
        val amount = parseAmount(snapshot.amount)
        if (snapshot.title.isBlank() || amount == null) {
            emit(ExpenseFormEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        val selectedParticipants = snapshot.participants.filter { it.isSelected }
        if (selectedParticipants.isEmpty()) {
            emit(ExpenseFormEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        if (snapshot.status == ExpenseFormStatus.Paid && snapshot.paidById == null) {
            emit(ExpenseFormEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                expenseRepository.updateExpense(
                    expenseId = expenseId,
                    request = ExpenseUpdateRequest(
                        title = snapshot.title.trim(),
                        amount = amount,
                        status = snapshot.status.toApiStatus(),
                        paidById = snapshot.paidById,
                        date = if (snapshot.status == ExpenseFormStatus.Paid) selectedDate?.toString() else null,
                        splitType = snapshot.splitType.toApiSplitType(),
                        note = snapshot.note.trim().ifBlank { null },
                        participants = buildParticipants(snapshot)
                    )
                )
            }) {
                is ApiResult.Success -> {
                    emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_saved_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(ExpenseFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    _state.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    private fun deleteExpense() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                expenseRepository.deleteExpense(expenseId)
            }) {
                is ApiResult.Success -> {
                    emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_deleted_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(ExpenseFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun buildParticipants(snapshot: ExpenseFormState): List<ExpenseParticipantInput> {
        val selected = snapshot.participants.filter { it.isSelected }
        val splitType = snapshot.splitType
        return selected.map { participant ->
            ExpenseParticipantInput(
                userId = participant.id,
                shareAmount = if (splitType == ExpenseSplitType.CustomAmounts) parseAmount(
                    participant.customAmount
                ) else null,
                isIncluded = true,
                isPaid = snapshot.status == ExpenseFormStatus.Paid && participant.id == snapshot.paidById
            )
        }
    }

    private fun moneyInput(value: String): String {
        return moneyInput(value, TextInputLimits.EXPENSE_AMOUNT)
    }

    private fun moneyInput(value: String, maxLength: Int): String {
        return value
            .filter { it.isDigit() || it == '.' || it == ',' }
            .take(maxLength)
    }

    private fun emit(effect: ExpenseFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class ExpensePayload(
        val expense: ExpenseDto,
        val members: List<MemberDto>,
        val currencyCode: String,
    )
}

private fun formatDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()))
}

private fun parseAmount(value: String): Double? {
    val normalized = value.replace(',', '.').trim()
    return normalized.toDoubleOrNull()
}

private fun ExpenseFormStatus.toApiStatus(): String = when (this) {
    ExpenseFormStatus.Planned -> "planned"
    ExpenseFormStatus.Paid -> "paid"
}

private fun ExpenseSplitType.toApiSplitType(): String = when (this) {
    ExpenseSplitType.SplitEqually -> "equally"
    ExpenseSplitType.CustomAmounts -> "custom"
}

private fun String.toFormStatus(): ExpenseFormStatus = when (this) {
    "planned" -> ExpenseFormStatus.Planned
    else -> ExpenseFormStatus.Paid
}

private fun String.toFormSplitType(): ExpenseSplitType = when (this) {
    "custom" -> ExpenseSplitType.CustomAmounts
    else -> ExpenseSplitType.SplitEqually
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}

private fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
}
