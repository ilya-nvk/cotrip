package nvk.cotrip.ui.idea.form

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
class EditIdeaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel(), IdeaFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.EditIdea.ARG_TRIP_ID))
    private val ideaId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.EditIdea.ARG_IDEA_ID))

    private val cities = listOf(
        "Paris",
        "Rome",
        "Barcelona",
        "Versailles",
    )

    private val _state = MutableStateFlow(
        IdeaFormState(
            mode = IdeaFormMode.Edit,
            ideaId = ideaId,
            title = "Visit the Louvre Museum",
            city = "Paris",
            currencySymbol = "€",
            costAmount = "15",
            costType = IdeaCostType.PerPerson,
            website = "https://www.louvre.fr",
            notes = "Book tickets online in advance. Free on first Sunday of each month. Home to the Mona Lisa and Venus de Milo.",
            isSaving = false,
            cityPicker = null
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<IdeaFormEffect>()
    override val effects = _effects.asSharedFlow()

    override fun onEvent(event: IdeaFormEvent) {
        when (event) {
            IdeaFormEvent.OnBackClick -> appNavigator.popBackStack()
            IdeaFormEvent.OnPrimaryClick -> {
                emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_saved_toast))
                appNavigator.popBackStack()
            }

            IdeaFormEvent.OnDeleteClick -> {
                emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_deleted_toast))
                appNavigator.popBackStack()
            }

            IdeaFormEvent.OnCityClick -> _state.update {
                it.copy(
                    cityPicker = IdeaCityPickerState(
                        cities
                    )
                )
            }

            IdeaFormEvent.OnDismissCityPicker -> _state.update { it.copy(cityPicker = null) }
            is IdeaFormEvent.OnCitySelected -> _state.update {
                it.copy(city = event.city, cityPicker = null)
            }

            is IdeaFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is IdeaFormEvent.OnCityChange -> _state.update { it.copy(city = event.value) }
            is IdeaFormEvent.OnCostAmountChange -> _state.update {
                it.copy(costAmount = event.value.filter { c -> c.isDigit() || c == '.' || c == ',' })
            }

            is IdeaFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is IdeaFormEvent.OnWebsiteChange -> _state.update { it.copy(website = event.value) }
            is IdeaFormEvent.OnNotesChange -> _state.update { it.copy(notes = event.value) }
        }
    }

    private fun emit(effect: IdeaFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
