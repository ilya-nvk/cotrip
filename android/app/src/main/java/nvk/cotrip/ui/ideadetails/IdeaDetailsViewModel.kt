package nvk.cotrip.ui.ideadetails

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
import nvk.cotrip.ui.ideas.IdeaDayOptionUi
import nvk.cotrip.ui.ideas.IdeaDayPickerState
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class IdeaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.IdeaDetails.ARG_TRIP_ID])
    private val ideaId: String =
        checkNotNull(savedStateHandle[Destination.IdeaDetails.ARG_IDEA_ID])

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
        IdeaDetailsState(
            tripId = tripId,
            ideaId = ideaId,
            title = "Visit the Louvre Museum",
            city = "Paris",
            cost = "€15 per person",
            website = "https://www.louvre.fr",
            notes = "Book tickets online in advance. Free on first Sunday of each month. Home to the Mona Lisa and Venus de Milo.",
            addedDay = null,
            selectedTab = IdeaDetailsTab.Details,
            commentsCount = 6,
            discussion = listOf(
                IdeaDiscussionItemUi.Message(
                    id = "m1",
                    author = "Sophie Martin",
                    initials = "SM",
                    text = "The Louvre gets super busy and lines can be long, especially in summer. Should we book a skip-the-line ticket?",
                    time = "2 hours ago",
                    isMe = false
                ),
                IdeaDiscussionItemUi.Message(
                    id = "m2",
                    author = "You",
                    initials = "ME",
                    text = "Good idea! I can book tickets for all of us if you want.",
                    time = "1 hour ago",
                    isMe = true
                ),
                IdeaDiscussionItemUi.System(
                    id = "s1",
                    text = "Ilya edited the idea",
                    time = "50 minutes ago",
                ),
                IdeaDiscussionItemUi.Message(
                    id = "m3",
                    author = "Emma Chen",
                    initials = "EC",
                    text = "Perfect! Let's aim for the morning so we have more time to explore.",
                    time = "45 minutes ago",
                    isMe = false
                ),
                IdeaDiscussionItemUi.System(
                    id = "s2",
                    text = "Ilya added this idea to the itinerary",
                    time = "30 minutes ago",
                ),
                IdeaDiscussionItemUi.Message(
                    id = "m4",
                    author = "You",
                    initials = "ME",
                    text = "I've added it to Day 2. We can visit in the morning and have lunch nearby after.",
                    time = "28 minutes ago",
                    isMe = true
                ),
            ),
            commentInput = "",
            dayPicker = null
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<IdeaDetailsEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: IdeaDetailsEvent) {
        when (event) {
            IdeaDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            IdeaDetailsEvent.OnEditClick -> appNavigator.navigate(
                Destination.EditIdea(tripId, ideaId)
            )

            IdeaDetailsEvent.OnAddToItineraryClick -> openDayPicker()
            IdeaDetailsEvent.OnDeleteClick -> {
                emit(IdeaDetailsEffect.ShowToastRes(R.string.idea_details_deleted_toast))
                appNavigator.popBackStack()
            }

            IdeaDetailsEvent.OnDismissDayPicker -> dismissDayPicker()
            is IdeaDetailsEvent.OnDaySelected -> selectDay(event.day)
            is IdeaDetailsEvent.OnTabSelected -> _state.update { it.copy(selectedTab = event.tab) }
            is IdeaDetailsEvent.OnCommentChange -> _state.update { it.copy(commentInput = event.value) }
            IdeaDetailsEvent.OnSendComment -> sendComment()
        }
    }

    private fun openDayPicker() {
        if (_state.value.addedDay != null) return
        _state.update { it.copy(dayPicker = IdeaDayPickerState(ideaId = ideaId, days = days)) }
    }

    private fun dismissDayPicker() {
        _state.update { it.copy(dayPicker = null) }
    }

    private fun selectDay(day: IdeaDayOptionUi) {
        _state.update { it.copy(addedDay = day.dayNumber, dayPicker = null) }
        emit(IdeaDetailsEffect.ShowToastRes(R.string.idea_details_added_toast))
    }

    private fun sendComment() {
        val input = _state.value.commentInput.trim()
        if (input.isBlank()) return
        _state.update { current ->
            val updated = current.discussion + IdeaDiscussionItemUi.Message(
                id = "m" + (current.discussion.size + 1),
                author = "You",
                initials = "ME",
                text = input,
                time = "just now",
                isMe = true
            )
            current.copy(
                discussion = updated,
                commentInput = "",
                commentsCount = current.commentsCount + 1
            )
        }
    }

    private fun emit(effect: IdeaDetailsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
