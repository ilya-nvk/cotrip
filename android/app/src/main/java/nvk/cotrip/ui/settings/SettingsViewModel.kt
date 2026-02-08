package nvk.cotrip.ui.settings

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
class SettingsViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            profile = SettingsProfileUi(
                name = "Sarah Chen",
                initials = "SC",
                hasPhoto = true
            ),
            notificationSections = listOf(
                SettingsNotificationSectionUi(
                    title = "DISCUSSIONS",
                    items = listOf(
                        SettingsToggleUi(
                            key = "discussions_comments",
                            title = "New comments in ideas",
                            enabled = true
                        )
                    )
                ),
                SettingsNotificationSectionUi(
                    title = "ITINERARY",
                    items = listOf(
                        SettingsToggleUi(
                            key = "itinerary_changes",
                            title = "Itinerary changes",
                            enabled = true
                        )
                    )
                ),
                SettingsNotificationSectionUi(
                    title = "EXPENSES",
                    items = listOf(
                        SettingsToggleUi(
                            key = "expenses_new",
                            title = "New expenses",
                            enabled = true
                        ),
                        SettingsToggleUi(
                            key = "expenses_settlements",
                            title = "Expense settlements",
                            enabled = true
                        )
                    )
                ),
                SettingsNotificationSectionUi(
                    title = "TRIPS",
                    items = listOf(
                        SettingsToggleUi(
                            key = "trips_added",
                            title = "Added to a trip",
                            enabled = true
                        ),
                        SettingsToggleUi(
                            key = "trips_date_changes",
                            title = "Trip date changes",
                            enabled = false
                        )
                    )
                )
            ),
            showDeleteDialog = false
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.OnBackClick -> appNavigator.popBackStack()
            SettingsEvent.OnChangePhotoClick -> {
                _state.update { current ->
                    current.copy(profile = current.profile.copy(hasPhoto = true))
                }
                emit(SettingsEffect.ShowToastRes(R.string.settings_photo_changed_toast))
            }

            SettingsEvent.OnRemovePhotoClick -> {
                _state.update { current ->
                    current.copy(profile = current.profile.copy(hasPhoto = false))
                }
            }

            is SettingsEvent.OnNameChange -> _state.update { current ->
                current.copy(profile = current.profile.copy(name = event.value))
            }

            is SettingsEvent.OnToggleNotifications -> _state.update { current ->
                current.copy(
                    notificationSections = current.notificationSections.map { section ->
                        section.copy(
                            items = section.items.map { item ->
                                if (item.key == event.key) item.copy(enabled = event.enabled) else item
                            }
                        )
                    }
                )
            }

            SettingsEvent.OnLogoutClick -> appNavigator.navigate(Destination.SignIn)
            SettingsEvent.OnDeleteProfileClick -> _state.update { it.copy(showDeleteDialog = true) }
            SettingsEvent.OnDismissDeleteDialog -> _state.update { it.copy(showDeleteDialog = false) }
            SettingsEvent.OnConfirmDeleteProfileClick -> {
                _state.update { it.copy(showDeleteDialog = false) }
                emit(SettingsEffect.ShowToastRes(R.string.settings_profile_deleted_toast))
                appNavigator.navigate(Destination.SignIn)
            }
        }
    }

    private fun emit(effect: SettingsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
