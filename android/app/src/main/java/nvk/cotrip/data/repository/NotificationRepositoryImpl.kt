package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.cache.NotificationsCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationReadBulkRequest
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.NotificationSettingsUpdateRequest
import nvk.cotrip.data.network.dto.PushTokenUpsertRequest
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncNotificationReadUpsertPayload
import nvk.cotrip.data.sync.SyncNotificationSettingsUpsertPayload
import nvk.cotrip.data.sync.SyncQueueRepository
import java.io.IOException
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val notificationsCacheStore: NotificationsCacheStore,
    private val syncQueueRepository: SyncQueueRepository,
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
            try {
                api.markNotificationRead(id).requireSuccess()
            } catch (e: IOException) {
                syncQueueRepository.enqueueUpsert(
                    entity = SyncEntities.NOTIFICATION_READ,
                    id = "single",
                    payload = SyncNotificationReadUpsertPayload(
                        mode = "single",
                        notificationId = id,
                    )
                )
            }
        } else {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.NOTIFICATION_READ,
                id = "single",
                payload = SyncNotificationReadUpsertPayload(
                    mode = "single",
                    notificationId = id,
                )
            )
        }
        safeLocalMutation("markRead(notificationId=$id)") {
            notificationsCacheStore.markRead(id)
        }
    }

    override suspend fun markReadBulkNonComment(): Result<Int> {
        val remoteResult = if (networkStateProvider.isOnline()) {
            runCatching {
                api.markNotificationsReadBulk(
                    NotificationReadBulkRequest(mode = "non_comment")
                ).updated
            }.onFailure { error ->
                if (error is IOException) {
                    syncQueueRepository.enqueueUpsert(
                        entity = SyncEntities.NOTIFICATION_READ,
                        id = "non_comment",
                        payload = SyncNotificationReadUpsertPayload(mode = "non_comment"),
                    )
                }
            }
        } else {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.NOTIFICATION_READ,
                id = "non_comment",
                payload = SyncNotificationReadUpsertPayload(mode = "non_comment"),
            )
            Result.success(0)
        }
        safeLocalMutation("markReadBulkNonComment") {
            notificationsCacheStore.markReadBulkNonComment()
        }
        return remoteResult.recoverCatching { error ->
            if (error is IOException) {
                0
            } else {
                throw error
            }
        }
    }

    override suspend fun markReadBulkIdeaComments(ideaId: String): Result<Int> {
        val remoteResult = if (networkStateProvider.isOnline()) {
            runCatching {
                api.markNotificationsReadBulk(
                    NotificationReadBulkRequest(
                        mode = "idea_comments",
                        ideaId = ideaId
                    )
                ).updated
            }.onFailure { error ->
                if (error is IOException) {
                    syncQueueRepository.enqueueUpsert(
                        entity = SyncEntities.NOTIFICATION_READ,
                        id = "idea_comments",
                        payload = SyncNotificationReadUpsertPayload(
                            mode = "idea_comments",
                            ideaId = ideaId,
                        ),
                    )
                }
            }
        } else {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.NOTIFICATION_READ,
                id = "idea_comments",
                payload = SyncNotificationReadUpsertPayload(
                    mode = "idea_comments",
                    ideaId = ideaId,
                ),
            )
            Result.success(0)
        }
        safeLocalMutation("markReadBulkIdeaComments(ideaId=$ideaId)") {
            notificationsCacheStore.markReadBulkIdeaComments(ideaId)
        }
        return remoteResult.recoverCatching { error ->
            if (error is IOException) {
                0
            } else {
                throw error
            }
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
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.NOTIFICATION_SETTINGS,
                id = "me",
                payload = SyncNotificationSettingsUpsertPayload(items = items),
            )
            safeLocalMutation("updateSettings.offlineSetSettings") {
                notificationsCacheStore.setSettings(items)
            }
            return Result.success(Unit)
        }
        return runCatching {
            val response = api.updateNotificationSettings(
                NotificationSettingsUpdateRequest(items = items)
            )
            safeLocalMutation("updateSettings.setSettings") {
                notificationsCacheStore.setSettings(response.items)
            }
        }.recoverCatching { error ->
            if (error !is IOException) throw error
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.NOTIFICATION_SETTINGS,
                id = "me",
                payload = SyncNotificationSettingsUpsertPayload(items = items),
            )
            safeLocalMutation("updateSettings.offlineFallbackSetSettings") {
                notificationsCacheStore.setSettings(items)
            }
            Unit
        }
    }

    override suspend fun upsertPushToken(token: String, platform: String): Result<Unit> {
        if (!networkStateProvider.isOnline()) {
            return Result.failure(IOException("Push token registration requires network"))
        }
        return runCatching {
            api.upsertPushToken(
                PushTokenUpsertRequest(
                    token = token,
                    platform = platform
                )
            ).requireSuccess()
        }
    }

    override suspend fun deletePushToken(token: String): Result<Unit> {
        if (!networkStateProvider.isOnline()) {
            return Result.failure(IOException("Push token deletion requires network"))
        }
        return runCatching {
            api.deletePushToken(token).requireSuccess()
        }
    }
}
