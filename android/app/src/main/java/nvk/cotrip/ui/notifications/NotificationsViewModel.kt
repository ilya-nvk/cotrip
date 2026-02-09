package nvk.cotrip.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
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
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val notificationRepository: NotificationRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val _state = MutableStateFlow(
        NotificationsState(
            isLoading = true,
            items = emptyList()
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<NotificationsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadNotifications()
    }

    fun onEvent(event: NotificationsEvent) {
        when (event) {
            NotificationsEvent.OnBackClick -> appNavigator.popBackStack()
            NotificationsEvent.OnRefresh -> loadNotifications()
            is NotificationsEvent.OnNotificationClick -> markRead(event.id)
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) { notificationRepository.listNotifications() }
            }) {
                is ApiResult.Success -> {
                    val items = result.data.map { it.toUi() }
                    _state.update { it.copy(isLoading = false, items = items) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false) }
                    emit(NotificationsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun markRead(id: String) {
        val current = _state.value.items.firstOrNull { it.id == id } ?: return
        if (current.isRead) return
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) { notificationRepository.markRead(id) }
            }) {
                is ApiResult.Success -> {
                    _state.update { st ->
                        st.copy(
                            items = st.items.map { item ->
                                if (item.id == id) item.copy(isRead = true) else item
                            }
                        )
                    }
                }
                is ApiResult.Failure ->
                    emit(NotificationsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
            }
        }
    }

    private fun emit(effect: NotificationsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}

private fun NotificationDto.toUi(): NotificationItemUi {
    val title = type
        .replace("_", " ")
        .replace("-", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        .ifBlank { "Notification" }
    val subtitle = payload.toString().takeIf { it != "null" }?.let { raw ->
        if (raw.length > 120) raw.take(117) + "..." else raw
    }
    val timestamp = runCatching {
        OffsetDateTime.parse(createdAt)
            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault()))
    }.getOrElse { createdAt }
    return NotificationItemUi(
        id = id,
        title = title,
        subtitle = subtitle,
        timestamp = timestamp,
        isRead = readAt != null
    )
}
