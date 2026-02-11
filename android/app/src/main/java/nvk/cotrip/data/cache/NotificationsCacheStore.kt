package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto

interface NotificationsCacheStore {
    val notifications: Flow<List<NotificationDto>>
    val settings: Flow<List<NotificationSettingDto>>

    suspend fun getNotifications(): List<NotificationDto>
    suspend fun getSettings(): List<NotificationSettingDto>
    suspend fun setNotifications(items: List<NotificationDto>)
    suspend fun markRead(notificationId: String)
    suspend fun setSettings(items: List<NotificationSettingDto>)
    suspend fun clear()
}
