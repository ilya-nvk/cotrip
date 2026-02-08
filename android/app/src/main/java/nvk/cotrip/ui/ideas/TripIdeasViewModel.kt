package nvk.cotrip.ui.ideas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class TripIdeasViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripIdeas.ARG_TRIP_ID])

    private val days = listOf(
        IdeaDayOptionUi(
            id = "d1",
            dayNumber = 1,
            dateText = "Thu, Jul 15",
            city = "Paris"
        ),
        IdeaDayOptionUi(
            id = "d2",
            dayNumber = 2,
            dateText = "Fri, Jul 16",
            city = "Paris"
        ),
        IdeaDayOptionUi(
            id = "d3",
            dayNumber = 3,
            dateText = "Sat, Jul 17",
            city = "Paris"
        ),
        IdeaDayOptionUi(
            id = "d4",
            dayNumber = 4,
            dateText = "Sun, Jul 18",
            city = "Versailles"
        ),
    )

    private val _state = MutableStateFlow(
        TripIdeasState(
            tripId = tripId,
            ideas = listOf(
                IdeaListItemUi(
                    id = "i1",
                    title = "Visit the Louvre Museum",
                    city = "Paris",
                    cost = "€15",
                    commentsCount = 3,
                    addedDay = 2
                ),
                IdeaListItemUi(
                    id = "i2",
                    title = "Sunset at Eiffel Tower",
                    city = "Paris",
                    cost = null,
                    commentsCount = 0,
                    addedDay = null
                ),
                IdeaListItemUi(
                    id = "i3",
                    title = "Food tour in Le Marais",
                    city = "Paris",
                    cost = "€75",
                    commentsCount = 7,
                    addedDay = 3
                ),
                IdeaListItemUi(
                    id = "i4",
                    title = "Day trip to Versailles",
                    city = "Versailles",
                    cost = "€40",
                    commentsCount = 1,
                    addedDay = null
                ),
            ),
            dayPicker = null
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripIdeasEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: TripIdeasEvent) {
        when (event) {
            TripIdeasEvent.OnBackClick -> appNavigator.popBackStack()
            TripIdeasEvent.OnAddIdeaClick -> appNavigator.navigate(
                Destination.CreateIdea(tripId)
            )

            is TripIdeasEvent.OnIdeaClick -> appNavigator.navigate(
                Destination.IdeaDetails(tripId, event.ideaId)
            )

            is TripIdeasEvent.OnAddToItineraryClick -> openDayPicker(event.ideaId)
            TripIdeasEvent.OnDismissDayPicker -> dismissDayPicker()
            is TripIdeasEvent.OnDaySelected -> selectDay(event.day)
        }
    }

    private fun openDayPicker(ideaId: String) {
        _state.update { current ->
            current.copy(dayPicker = IdeaDayPickerState(ideaId = ideaId, days = days))
        }
    }

    private fun dismissDayPicker() {
        _state.update { it.copy(dayPicker = null) }
    }

    private fun selectDay(day: IdeaDayOptionUi) {
        val ideaId = _state.value.dayPicker?.ideaId ?: return
        _state.update { current ->
            current.copy(
                dayPicker = null,
                ideas = current.ideas.map { idea ->
                    if (idea.id == ideaId) idea.copy(addedDay = day.dayNumber) else idea
                }
            )
        }
        emit(TripIdeasEffect.ShowToastRes(R.string.ideas_added_to_itinerary_toast))
    }

    private fun emit(effect: TripIdeasEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
