package nvk.cotrip.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto
import javax.inject.Inject

class NotificationsCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : NotificationsCacheStore {

    override val notifications: Flow<List<NotificationDto>> = dataStore.data.map { prefs ->
        decodeNotifications(prefs[NOTIFICATIONS_KEY]).items
    }

    override val settings: Flow<List<NotificationSettingDto>> = dataStore.data.map { prefs ->
        decodeSettings(prefs[NOTIFICATION_SETTINGS_KEY]).items
    }

    override suspend fun getNotifications(): List<NotificationDto> {
        val prefs = dataStore.data.first()
        return decodeNotifications(prefs[NOTIFICATIONS_KEY]).items
    }

    override suspend fun getSettings(): List<NotificationSettingDto> {
        val prefs = dataStore.data.first()
        return decodeSettings(prefs[NOTIFICATION_SETTINGS_KEY]).items
    }

    override suspend fun setNotifications(items: List<NotificationDto>) {
        dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_KEY] = json.encodeToString(
                NotificationsPayload.serializer(),
                NotificationsPayload(items = items)
            )
        }
    }

    override suspend fun markRead(notificationId: String) {
        markReadWhere { item ->
            item.id == notificationId && item.readAt == null
        }
    }

    override suspend fun markReadBulkNonComment() {
        markReadWhere { item ->
            item.type != IDEA_COMMENT_TYPE && item.readAt == null
        }
    }

    override suspend fun markReadBulkIdeaComments(ideaId: String) {
        markReadWhere { item ->
            if (item.type != IDEA_COMMENT_TYPE || item.readAt != null) return@markReadWhere false
            runCatching {
                item.payload.jsonObject["ideaId"]?.jsonPrimitive?.contentOrNull == ideaId
            }.getOrDefault(false)
        }
    }

    private suspend fun markReadWhere(predicate: (NotificationDto) -> Boolean) {
        dataStore.edit { prefs ->
            val current = decodeNotifications(prefs[NOTIFICATIONS_KEY])
            val updated = current.items.map { item ->
                if (predicate(item)) {
                    item.copy(readAt = item.createdAt)
                } else {
                    item
                }
            }
            prefs[NOTIFICATIONS_KEY] = json.encodeToString(
                NotificationsPayload.serializer(),
                NotificationsPayload(items = updated)
            )
        }
    }

    override suspend fun setSettings(items: List<NotificationSettingDto>) {
        dataStore.edit { prefs ->
            prefs[NOTIFICATION_SETTINGS_KEY] = json.encodeToString(
                NotificationSettingsPayload.serializer(),
                NotificationSettingsPayload(items = items)
            )
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(NOTIFICATIONS_KEY)
            prefs.remove(NOTIFICATION_SETTINGS_KEY)
        }
    }

    private fun decodeNotifications(raw: String?): NotificationsPayload {
        if (raw.isNullOrBlank()) return NotificationsPayload()
        return runCatching {
            json.decodeFromString(NotificationsPayload.serializer(), raw)
        }.getOrElse { NotificationsPayload() }
    }

    private fun decodeSettings(raw: String?): NotificationSettingsPayload {
        if (raw.isNullOrBlank()) return NotificationSettingsPayload()
        return runCatching {
            json.decodeFromString(NotificationSettingsPayload.serializer(), raw)
        }.getOrElse { NotificationSettingsPayload() }
    }

    @Serializable
    private data class NotificationsPayload(
        val items: List<NotificationDto> = emptyList(),
    )

    @Serializable
    private data class NotificationSettingsPayload(
        val items: List<NotificationSettingDto> = emptyList(),
    )

    private companion object {
        private const val IDEA_COMMENT_TYPE = "idea_comment"
        private val NOTIFICATIONS_KEY = stringPreferencesKey("notifications_cache")
        private val NOTIFICATION_SETTINGS_KEY = stringPreferencesKey("notification_settings_cache")
    }
}
