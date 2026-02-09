package nvk.cotrip.data.repository

import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto

interface NotificationRepository {
    suspend fun listNotifications(): List<NotificationDto>
    suspend fun markRead(id: String)
    suspend fun listSettings(): List<NotificationSettingDto>
    suspend fun updateSettings(items: List<NotificationSettingDto>): List<NotificationSettingDto>
}
