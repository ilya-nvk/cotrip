package nvk.cotrip.data.repository

interface PushTokenRepository {
    suspend fun upsert(token: String, platform: String = "android")
    suspend fun delete(token: String)
}
