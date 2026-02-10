package nvk.cotrip.ui.trip.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.repository.PendingTripCreationStore
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.util.AppLogger
import java.time.OffsetDateTime
import javax.inject.Inject

@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val pendingTripCreationStore: PendingTripCreationStore,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {
    private companion object {
        private const val TAG = "CreateTripVM"
    }

    private val _state = MutableStateFlow(TripFormState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripFormEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: TripFormEvent) {
        when (event) {
            TripFormEvent.OnCloseClick,
            TripFormEvent.OnCancelClick -> closeScreen()

            TripFormEvent.OnPickCoverClick -> emitToastRes(R.string.trip_form_cover_not_implemented)

            is TripFormEvent.OnNameChange -> {
                _state.update { it.copy(name = event.value) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnStartDateSelected -> {
                _state.update { it.copy(startDate = event.date) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnEndDateSelected -> {
                _state.update { it.copy(endDate = event.date) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnDescriptionChange ->
                _state.update { it.copy(description = event.value) }

            is TripFormEvent.OnCurrencySelect ->
                _state.update { it.copy(currency = event.currency) }

            TripFormEvent.OnPrimaryActionClick -> {
                val s = state.value
                if (!s.canSubmit || s.isLoading) return
                createTrip()
            }

            TripFormEvent.OnArchiveClick,
            TripFormEvent.OnDeleteClick -> Unit
        }
    }

    private fun recomputeCanSubmit() {
        _state.update { s ->
            val hasName = s.name.isNotBlank()
            val hasDates = s.startDate != null && s.endDate != null
            val datesOk = if (hasDates) !s.endDate!!.isBefore(s.startDate) else false
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
            _state.update { it.copy(isLoading = true) }
            val request = CreateTripRequest(
                title = s.name,
                description = s.description.takeIf { it.isNotBlank() },
                startDate = startDate.toString(),
                endDate = endDate.toString(),
                locationLine = null,
                coverUrl = null,
                currencyCode = s.currency.code,
            )
            AppLogger.i(
                TAG,
                "createTrip started title='${request.title}' start=${request.startDate} end=${request.endDate}"
            )

            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.createTrip(request)
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    AppLogger.i(TAG, "createTrip succeeded tripId=${result.data.id}")
                    markTripCreationPending(result.data.id)
                    emitToastRes(R.string.create_trip_created_toast)
                    appNavigator.navigate(
                        Destination.TripItinerary(
                            tripId = result.data.id,
                            requireCities = true,
                            creationFlow = true,
                        )
                    )
                }

                is ApiResult.Failure -> {
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

    private suspend fun recoverCreatedTripId(request: CreateTripRequest): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                tripRepository.refreshTrips()
                val candidates = tripRepository.listTrips()
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
        withContext(Dispatchers.IO) {
            runCatching { pendingTripCreationStore.setPendingTripId(tripId) }
                .onFailure { AppLogger.w(TAG, "Failed to mark pending tripId=$tripId", it) }
        }
    }
}
