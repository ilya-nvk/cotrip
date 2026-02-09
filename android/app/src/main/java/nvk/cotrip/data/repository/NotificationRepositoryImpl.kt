package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.NotificationSettingsUpdateRequest

class NotificationRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
) : NotificationRepository {
    override suspend fun listNotifications(): List<NotificationDto> {
        return api.listNotifications().items
    }

    override suspend fun markRead(id: String) {
        api.markNotificationRead(id)
    }

    override suspend fun listSettings(): List<NotificationSettingDto> {
        return api.getNotificationSettings().items
    }

    override suspend fun updateSettings(items: List<NotificationSettingDto>): List<NotificationSettingDto> {
        val response = api.updateNotificationSettings(
            NotificationSettingsUpdateRequest(items = items)
        )
        return response.items
    }
}
