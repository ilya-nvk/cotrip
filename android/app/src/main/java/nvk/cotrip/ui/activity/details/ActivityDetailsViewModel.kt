package nvk.cotrip.ui.activity.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ActivityDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val activityId: String =
        (savedStateHandle.get<String>(Destination.ActivityDetails.ARG_ACTIVITY_ID)).orEmpty()

    private val _state = MutableStateFlow(
        ActivityDetailsState(
            dayId = "",
            activityId = activityId,
            dayAndCity = "",
            title = "",
            dateText = "",
            timeText = "",
            locationName = null,
            costText = null,
            website = null,
            notes = null,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ActivityDetailsEffect>()
    val effects = _effects.asSharedFlow()
    private var currentTripId: String? = null

    init {
        loadActivity()
    }

    fun onEvent(event: ActivityDetailsEvent) {
        when (event) {
            ActivityDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            ActivityDetailsEvent.OnRefresh -> loadActivity()
            ActivityDetailsEvent.OnEditClick -> appNavigator.navigate(
                Destination.EditActivity(activityId)
            )

            ActivityDetailsEvent.OnOpenLocationClick -> emitToast(R.string.activity_details_open_location_not_implemented)
            ActivityDetailsEvent.OnOpenWebsiteClick -> emitToast(R.string.activity_details_open_website_not_implemented)
            ActivityDetailsEvent.OnDeleteClick -> deleteActivity()
        }
    }

    private fun loadActivity() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) { findActivity(activityId) }
            }) {
                is ApiResult.Success -> {
                    val info = result.data
                    currentTripId = info.trip.id
                    val currencySymbol = currencySymbolFor(info.trip.currencyCode)
                    val dateText = formatDay(info.day.date)
                    val dayAndCity = if (info.day.city.isNullOrBlank()) {
                        "Day ${info.day.dayNumber}"
                    } else {
                        "Day ${info.day.dayNumber} · ${info.day.city}"
                    }
                    _state.value = ActivityDetailsState(
                        dayId = info.day.id,
                        activityId = info.activity.id,
                        dayAndCity = dayAndCity,
                        title = info.activity.title,
                        dateText = dateText,
                        timeText = info.activity.timeText.orEmpty(),
                        locationName = info.activity.locationName,
                        costText = info.activity.costAmount?.let { formatCost(it, currencySymbol) },
                        website = info.activity.website,
                        notes = info.activity.notes,
                    )
                }

                is ApiResult.Failure -> emitToast(uiErrorMapper.messageRes(result))
            }
        }
    }

    private fun deleteActivity() {
        if (currentTripId == null) return
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    itineraryRepository.deleteActivity(activityId)
                }
            }) {
                is ApiResult.Success -> {
                    emitToast(R.string.activity_details_deleted_toast)
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> emitToast(uiErrorMapper.messageRes(result))
            }
        }
    }

    private suspend fun findActivity(activityId: String): ActivityLookup {
        val trips = tripRepository.listTrips()
        trips.forEach { trip ->
            val itinerary = itineraryRepository.getItinerary(trip.id)
            itinerary.forEach { day ->
                val match = day.activities.firstOrNull { it.id == activityId }
                if (match != null) {
                    return ActivityLookup(trip, day, match)
                }
            }
        }
        throw IllegalStateException("Activity not found")
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(ActivityDetailsEffect.ShowToastRes(resId)) }
    }

    private data class ActivityLookup(
        val trip: TripDto,
        val day: ItineraryDayDto,
        val activity: ActivityDto,
    )
}

private fun formatDay(date: String): String {
    val parsed = LocalDate.parse(date)
    return parsed.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}

private fun formatCost(amount: Double, currencySymbol: String): String {
    val display = if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}
