package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto

interface NotificationRepository {
    val notifications: Flow<List<NotificationDto>>
    val settings: Flow<List<NotificationSettingDto>>

    suspend fun refreshNotifications(): Result<Unit>
    suspend fun markRead(id: String)
    suspend fun refreshSettings(): Result<Unit>
    suspend fun updateSettings(items: List<NotificationSettingDto>): Result<Unit>
}
