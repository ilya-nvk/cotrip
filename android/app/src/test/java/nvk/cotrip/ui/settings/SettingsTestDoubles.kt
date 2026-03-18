package nvk.cotrip.ui.settings

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import nvk.cotrip.data.network.dto.AuthResponse
import nvk.cotrip.data.network.dto.AuthDevRequest
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.repository.AuthRepository
import nvk.cotrip.data.repository.ImageUploadRepository
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination

internal class SettingsFakeNavigator : AppNavigator {
    val destinations = mutableListOf<Destination>()
    var popCalls: Int = 0

    override fun navigate(
        destination: Destination,
        navOptions: (NavOptionsBuilder.() -> Unit)?,
    ) {
        destinations += destination
    }

    override fun popBackStack(): Boolean {
        popCalls += 1
        return true
    }
}

internal class SettingsFakeAuthRepository : AuthRepository {
    var logoutCalls: Int = 0
    var clearSessionCalls: Int = 0

    override fun hasSession(): Boolean = true

    override suspend fun signInWithGoogle(idToken: String): AuthResponse {
        error("Not expected in Settings tests")
    }

    override suspend fun signInWithDev(request: AuthDevRequest): AuthResponse {
        error("Not expected in Settings tests")
    }

    override suspend fun logout() {
        logoutCalls += 1
    }

    override fun clearSession() {
        clearSessionCalls += 1
    }
}

internal class SettingsFakeUserRepository(
    user: UserDto = settingsUserDto(),
) : UserRepository {
    override val me: MutableStateFlow<UserDto?> = MutableStateFlow(user)

    var refreshMeResult: Result<Unit> = Result.success(Unit)
    var updateError: Throwable? = null
    var deleteError: Throwable? = null
    var updateRequests = mutableListOf<UpdateUserRequest>()
    var deleteCalls: Int = 0
    var clearSessionCalls: Int = 0

    override suspend fun refreshMe(): Result<Unit> = refreshMeResult

    override suspend fun updateMe(request: UpdateUserRequest): UserDto {
        updateRequests += request
        updateError?.let { throw it }
        val current = me.value ?: settingsUserDto()
        val updated = current.copy(
            name = request.name,
            photoUrl = request.photoUrl?.takeIf { it.isNotBlank() },
            initials = request.name.trim().split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { current.initials },
        )
        me.value = updated
        return updated
    }

    override suspend fun deleteMe() {
        deleteCalls += 1
        deleteError?.let { throw it }
        me.value = null
    }

    override fun clearSession() {
        clearSessionCalls += 1
    }
}

internal class SettingsFakeImageUploadRepository : ImageUploadRepository {
    var uploadError: Throwable? = null
    var uploadResult: String = "https://cdn.example/avatar.jpg"
    val uploads = mutableListOf<String>()

    override suspend fun uploadImage(uriString: String): String {
        uploads += uriString
        uploadError?.let { throw it }
        return uploadResult
    }
}

internal class SettingsFakeNotificationRepository(
    initialSettings: List<NotificationSettingDto> = defaultNotificationSettings(),
) : NotificationRepository {
    override val notifications: Flow<List<NotificationDto>> = flowOf(emptyList())
    override val settings: MutableStateFlow<List<NotificationSettingDto>> = MutableStateFlow(initialSettings)

    var refreshSettingsResult: Result<Unit> = Result.success(Unit)
    var updateSettingsResult: Result<Unit> = Result.success(Unit)
    val updateRequests = mutableListOf<List<NotificationSettingDto>>()

    override suspend fun refreshNotifications(): Result<Unit> = Result.success(Unit)

    override suspend fun markRead(id: String) = Unit

    override suspend fun markReadBulkNonComment(): Result<Int> = Result.success(0)

    override suspend fun markReadBulkIdeaComments(ideaId: String): Result<Int> = Result.success(0)

    override suspend fun refreshSettings(): Result<Unit> = refreshSettingsResult

    override suspend fun updateSettings(items: List<NotificationSettingDto>): Result<Unit> {
        updateRequests += items
        if (updateSettingsResult.isSuccess) {
            settings.value = items
        }
        return updateSettingsResult
    }

    override suspend fun upsertPushToken(token: String, platform: String): Result<Unit> = Result.success(Unit)

    override suspend fun deletePushToken(token: String): Result<Unit> = Result.success(Unit)
}

internal fun settingsUserDto(
    id: String = "user-1",
    name: String = "Alice Cooper",
    photoUrl: String? = null,
): UserDto = UserDto(
    id = id,
    name = name,
    photoUrl = photoUrl,
    initials = "AC",
)

internal fun defaultNotificationSettings(): List<NotificationSettingDto> = listOf(
    NotificationSettingDto(key = "discussions_comments", enabled = true),
    NotificationSettingDto(key = "expenses_new", enabled = true),
    NotificationSettingDto(key = "expenses_settlements", enabled = true),
)

internal fun notificationDto(
    id: String,
    type: String,
    payload: JsonElement = buildJsonObject { },
): NotificationDto = NotificationDto(
    id = id,
    type = type,
    payload = payload,
    createdAt = "2026-03-16T10:00:00Z",
    readAt = null,
)
