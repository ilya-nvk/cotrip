package nvk.cotrip.ui.expense.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseParticipantInput
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.limitReachedDetails
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.LimitDialogState
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CreateExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel(), ExpenseFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.CreateExpense.ARG_TRIP_ID])

    private var members: List<MemberDto> = emptyList()
    private var currencyCode: String = "EUR"
    private var meId: String? = null
    private var selectedDate: LocalDate? = LocalDate.now()

    private val _state = MutableStateFlow(
        ExpenseFormState(
            mode = ExpenseFormMode.Create,
            expenseId = null,
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

    private val _effects = MutableSharedFlow<ExpenseFormEffect>()
    override val effects = _effects.asSharedFlow()

    init {
        loadMembers()
    }

    override fun onEvent(event: ExpenseFormEvent) {
        when (event) {
            ExpenseFormEvent.OnBackClick -> appNavigator.popBackStack()
            ExpenseFormEvent.OnPrimaryClick -> createExpense()
            ExpenseFormEvent.OnDeleteClick -> emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_delete_not_available))
            ExpenseFormEvent.OnDismissLimitDialog -> _state.update { it.copy(limitDialog = null) }
            ExpenseFormEvent.OnConfirmDeleteOldestAndRetry -> deleteOldestAndRetry()
            ExpenseFormEvent.OnDateClick -> Unit
            is ExpenseFormEvent.OnDateSelected -> selectDate(event.date)
            ExpenseFormEvent.OnPaidByClick -> _state.update { it.copy(paidByPickerVisible = true) }
            ExpenseFormEvent.OnDismissPaidByPicker -> _state.update { it.copy(paidByPickerVisible = false) }
            is ExpenseFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
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
                            customAmount = moneyInput(event.value)
                        )
                        else participant
                    }
                )
            }

            is ExpenseFormEvent.OnNoteChange -> _state.update { it.copy(note = event.value) }
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val trip = tripRepository.getTrip(tripId).first()
                    val members = tripRepository.tripMembers(tripId).first()
                    val me = checkNotNull(userRepository.me.first())
                    MembersPayload(
                        members = members,
                        currencyCode = trip.currencyCode,
                        meId = me.id
                    )
                }
            }) {
                is ApiResult.Success -> {
                    val payload = result.data
                    members = payload.members
                    currencyCode = payload.currencyCode
                    meId = payload.meId
                    val participants = payload.members.map { member ->
                        ExpenseParticipantUi(
                            id = member.userId,
                            initials = member.initials,
                            name = member.name,
                            photoUrl = member.photoUrl,
                            isSelected = true,
                            customAmount = ""
                        )
                    }
                    val dateText = selectedDate?.let { formatDate(it) }.orEmpty()
                    _state.update {
                        it.copy(
                            currencySymbol = currencySymbolFor(payload.currencyCode),
                            paidById = payload.meId,
                            dateText = dateText,
                            participants = participants
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
            val paidById =
                if (status == ExpenseFormStatus.Planned) null else current.paidById ?: meId
            val dateText =
                if (status == ExpenseFormStatus.Planned) "" else current.dateText.ifBlank {
                    selectedDate?.let { formatDate(it) }.orEmpty()
                }
            current.copy(
                status = status,
                paidById = paidById,
                dateText = dateText
            )
        }
    }

    private fun selectDate(date: LocalDate) {
        selectedDate = date
        _state.update { it.copy(dateText = formatDate(date)) }
    }

    private fun createExpense() {
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
                withContext(Dispatchers.IO) {
                    expenseRepository.createExpense(
                        tripId = tripId,
                        request = ExpenseCreateRequest(
                            title = snapshot.title.trim(),
                            amount = amount,
                            currencyCode = currencyCode,
                            status = snapshot.status.toApiStatus(),
                            paidById = snapshot.paidById,
                            date = if (snapshot.status == ExpenseFormStatus.Paid) selectedDate?.toString() else null,
                            splitType = snapshot.splitType.toApiSplitType(),
                            note = snapshot.note.trim().ifBlank { null },
                            participants = buildParticipants(snapshot)
                        )
                    )
                }
            }) {
                is ApiResult.Success -> {
                    emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_created_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    val limit = result.limitReachedDetails()
                    val oldest = limit?.oldestCandidate
                    if (oldest?.deletable == true) {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                limitDialog = LimitDialogState(
                                    oldestId = oldest.id,
                                    oldestLabel = oldest.label,
                                )
                            )
                        }
                    } else {
                        emit(ExpenseFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                        _state.update { it.copy(isSaving = false) }
                    }
                }
            }
        }
    }

    private fun deleteOldestAndRetry() {
        val dialog = _state.value.limitDialog ?: return
        val snapshot = _state.value
        _state.update { it.copy(limitDialog = null, isSaving = true) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    expenseRepository.deleteExpense(dialog.oldestId)
                    expenseRepository.createExpense(
                        tripId = tripId,
                        request = ExpenseCreateRequest(
                            title = snapshot.title.trim(),
                            amount = parseAmount(snapshot.amount) ?: 0.0,
                            currencyCode = currencyCode,
                            status = snapshot.status.toApiStatus(),
                            paidById = snapshot.paidById,
                            date = if (snapshot.status == ExpenseFormStatus.Paid) selectedDate?.toString() else null,
                            splitType = snapshot.splitType.toApiSplitType(),
                            note = snapshot.note.trim().ifBlank { null },
                            participants = buildParticipants(snapshot)
                        )
                    )
                }
            }) {
                is ApiResult.Success -> {
                    emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_created_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(ExpenseFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    _state.update { it.copy(isSaving = false) }
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
                // Manual settlement happens from expense details; creation should not auto-settle.
                isPaid = false
            )
        }
    }

    private fun moneyInput(value: String): String {
        return value.filter { it.isDigit() || it == '.' || it == ',' }
    }

    private fun emit(effect: ExpenseFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class MembersPayload(
        val members: List<MemberDto>,
        val currencyCode: String,
        val meId: String,
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

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}
