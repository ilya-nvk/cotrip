package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.cache.NotificationsCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.NotificationSettingsUpdateRequest
import nvk.cotrip.data.network.requireSuccess
import java.io.IOException
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val notificationsCacheStore: NotificationsCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : NotificationRepository {

    override val notifications: Flow<List<NotificationDto>> = notificationsCacheStore.notifications
    override val settings: Flow<List<NotificationSettingDto>> = notificationsCacheStore.settings

    override suspend fun refreshNotifications(): Result<Unit> {
        if (!networkStateProvider.isOnline()) {
            return Result.success(Unit)
        }
        return runCatching {
            val items = api.listNotifications().items
            safeLocalMutation("refreshNotifications.setNotifications") {
                notificationsCacheStore.setNotifications(items)
            }
        }
    }

    override suspend fun markRead(id: String) {
        if (networkStateProvider.isOnline()) {
            api.markNotificationRead(id).requireSuccess()
        }
        safeLocalMutation("markRead(notificationId=$id)") {
            notificationsCacheStore.markRead(id)
        }
    }

    override suspend fun refreshSettings(): Result<Unit> {
        if (!networkStateProvider.isOnline()) {
            return Result.success(Unit)
        }
        return runCatching {
            val items = api.getNotificationSettings().items
            safeLocalMutation("refreshSettings.setSettings") {
                notificationsCacheStore.setSettings(items)
            }
        }
    }

    override suspend fun updateSettings(items: List<NotificationSettingDto>): Result<Unit> {
        if (!networkStateProvider.isOnline()) {
            return Result.failure(IOException("Notification settings update requires network"))
        }
        return runCatching {
            val response = api.updateNotificationSettings(
                NotificationSettingsUpdateRequest(items = items)
            )
            safeLocalMutation("updateSettings.setSettings") {
                notificationsCacheStore.setSettings(response.items)
            }
        }
    }
}
