package nvk.cotrip.ui.trip.form

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
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.limitReachedDetails
import nvk.cotrip.data.repository.ImageUploadRepository
import nvk.cotrip.data.repository.PendingTripCreationStore
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.LimitDialogState
import nvk.cotrip.ui.common.TextInputLimits
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.util.AppLogger
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject

@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val pendingTripCreationStore: PendingTripCreationStore,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {
    private companion object {
        private const val TAG = "CreateTripVM"
    }

    private val _state = MutableStateFlow(TripFormState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripFormEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    fun onEvent(event: TripFormEvent) {
        when (event) {
            TripFormEvent.OnCloseClick,
            TripFormEvent.OnCancelClick -> closeScreen()

            TripFormEvent.OnPickCoverClick -> {
                viewModelScope.launch { _effects.emit(TripFormEffect.OpenImagePicker) }
            }

            is TripFormEvent.OnCoverPicked -> {
                val uri = event.uriString?.trim().orEmpty()
                if (uri.isNotBlank()) {
                    uploadCover(uri)
                }
            }

            is TripFormEvent.OnNameChange -> {
                _state.update { it.copy(name = event.value.take(TextInputLimits.TRIP_TITLE)) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnStartDateSelected -> {
                if (!TripDateRules.isStartDateAllowed(event.date, LocalDate.now())) {
                    emitToastRes(R.string.trip_form_start_date_range_toast)
                    return
                }
                _state.update { it.copy(startDate = event.date) }
                val selectedEnd = _state.value.endDate
                if (selectedEnd != null && !TripDateRules.isEndDateAllowed(
                        event.date,
                        selectedEnd
                    )
                ) {
                    emitToastRes(R.string.trip_form_end_date_range_toast)
                }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnEndDateSelected -> {
                val selectedStart = _state.value.startDate
                val start = selectedStart ?: LocalDate.now()
                if (!TripDateRules.isEndDateAllowed(startDate = start, endDate = event.date)) {
                    emitToastRes(R.string.trip_form_end_date_range_toast)
                    return
                }
                _state.update { it.copy(endDate = event.date) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnDescriptionChange ->
                _state.update { it.copy(description = event.value.take(TextInputLimits.TRIP_DESCRIPTION)) }

            is TripFormEvent.OnCurrencySelect ->
                _state.update { it.copy(currency = event.currency) }

            TripFormEvent.OnPrimaryActionClick -> {
                val s = state.value
                if (!s.canSubmit || s.isLoading) return
                createTrip()
            }

            TripFormEvent.OnDismissLimitDialog -> {
                _state.update { it.copy(limitDialog = null) }
            }

            TripFormEvent.OnConfirmDeleteOldestAndRetry -> {
                deleteOldestAndRetry()
            }

            TripFormEvent.OnArchiveClick,
            TripFormEvent.OnDeleteClick -> Unit
        }
    }

    private fun recomputeCanSubmit() {
        _state.update { s ->
            val hasName = s.name.isNotBlank()
            val hasDates = s.startDate != null && s.endDate != null
            val datesOk = if (hasDates) {
                val start = s.startDate!!
                val end = s.endDate!!
                TripDateRules.isStartDateAllowed(start, LocalDate.now()) &&
                        TripDateRules.isEndDateAllowed(startDate = start, endDate = end)
            } else {
                false
            }
            s.copy(canSubmit = hasName && hasDates && datesOk)
        }
    }

    private fun emitToastRes(resId: Int) {
        viewModelScope.launch { _effects.emit(TripFormEffect.ShowToastRes(resId)) }
    }

    private fun closeScreen() {
        appNavigator.popBackStack()
    }

    private fun createTrip() {
        viewModelScope.launch {
            val s = state.value
            val startDate = s.startDate ?: return@launch
            val endDate = s.endDate ?: return@launch
            if (!TripDateRules.isStartDateAllowed(startDate, LocalDate.now())) {
                emitToastRes(R.string.trip_form_start_date_range_toast)
                return@launch
            }
            if (!TripDateRules.isEndDateAllowed(startDate = startDate, endDate = endDate)) {
                emitToastRes(R.string.trip_form_end_date_range_toast)
                return@launch
            }
            _state.update { it.copy(isLoading = true) }
            val request = CreateTripRequest(
                title = s.name,
                description = s.description.takeIf { it.isNotBlank() },
                startDate = startDate.toString(),
                endDate = endDate.toString(),
                locationLine = null,
                coverUrl = s.coverUri,
                currencyCode = s.currency.code,
            )
            AppLogger.i(
                TAG,
                "createTrip started title='${request.title}' start=${request.startDate} end=${request.endDate}"
            )

            val result = apiCaller.call {
                tripRepository.createTrip(request)
            }

            when (result) {
                is ApiResult.Success -> {
                    AppLogger.i(TAG, "createTrip succeeded tripId=${result.data}")
                    markTripCreationPending(result.data)
                    emitToastRes(R.string.create_trip_created_toast)
                    appNavigator.navigate(
                        Destination.TripItinerary(
                            tripId = result.data,
                            requireCities = true,
                            creationFlow = true,
                        )
                    )
                }

                is ApiResult.Failure -> {
                    val limitDetails = result.limitReachedDetails()
                    val oldest = limitDetails?.oldestCandidate
                    if (oldest?.deletable == true) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                limitDialog = LimitDialogState(
                                    oldestId = oldest.id,
                                    oldestLabel = oldest.label,
                                )
                            )
                        }
                        return@launch
                    }
                    AppLogger.w(
                        TAG,
                        "createTrip failed code=${result.httpCode} apiCode=${result.error?.code.orEmpty()}",
                        result.cause
                    )
                    val recoveredTripId = recoverCreatedTripId(request)
                    if (recoveredTripId != null) {
                        AppLogger.i(TAG, "createTrip recovered via listTrips tripId=$recoveredTripId")
                        markTripCreationPending(recoveredTripId)
                        emitToastRes(R.string.create_trip_created_toast)
                        appNavigator.navigate(
                            Destination.TripItinerary(
                                tripId = recoveredTripId,
                                requireCities = true,
                                creationFlow = true,
                            )
                        )
                    } else {
                        emitToastRes(uiErrorMapper.messageRes(result))
                    }
                }
            }

            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun deleteOldestAndRetry() {
        val dialog = _state.value.limitDialog ?: return
        viewModelScope.launch {
            _state.update { it.copy(limitDialog = null, isLoading = true) }
            when (val result = apiCaller.call {
                tripRepository.deleteTrip(dialog.oldestId)
                val request = buildCreateTripRequest(state.value)
                tripRepository.createTrip(request)
            }) {
                is ApiResult.Success -> {
                    markTripCreationPending(result.data)
                    emitToastRes(R.string.create_trip_created_toast)
                    appNavigator.navigate(
                        Destination.TripItinerary(
                            tripId = result.data,
                            requireCities = true,
                            creationFlow = true,
                        )
                    )
                }

                is ApiResult.Failure -> {
                    emitToastRes(uiErrorMapper.messageRes(result))
                    _state.update { it.copy(isLoading = false) }
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun buildCreateTripRequest(state: TripFormState): CreateTripRequest {
        return CreateTripRequest(
            title = state.name,
            description = state.description.takeIf { it.isNotBlank() },
            startDate = checkNotNull(state.startDate).toString(),
            endDate = checkNotNull(state.endDate).toString(),
            locationLine = null,
            coverUrl = state.coverUri,
            currencyCode = state.currency.code,
        )
    }

    private suspend fun recoverCreatedTripId(request: CreateTripRequest): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                tripRepository.refreshTrips()
                val candidates = tripRepository.trips.first()
                    .filter {
                        it.title.trim() == request.title.trim() &&
                            it.startDate == request.startDate &&
                            it.endDate == request.endDate
                    }
                candidates.maxByOrNull { trip ->
                    runCatching { OffsetDateTime.parse(trip.updatedAt).toInstant().toEpochMilli() }
                        .getOrDefault(0L)
                }?.id
            }.onFailure {
                AppLogger.w(TAG, "recoverCreatedTripId failed", it)
            }.getOrNull()
        }
    }

    private suspend fun markTripCreationPending(tripId: String) {
        runCatching { pendingTripCreationStore.setPendingTripId(tripId) }
            .onFailure { AppLogger.w(TAG, "Failed to mark pending tripId=$tripId", it) }
    }

    private fun uploadCover(uriString: String) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, coverPreviewUri = uriString) }
            when (val result = apiCaller.call {
                imageUploadRepository.uploadImage(uriString)
            }) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            coverUri = result.data,
                            coverPreviewUri = result.data,
                        )
                    }
                    emitToastRes(R.string.trip_form_cover_uploaded_toast)
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, coverPreviewUri = it.coverUri) }
                    emitToastRes(uiErrorMapper.messageRes(result))
                }
            }
        }
    }
}
