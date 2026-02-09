package nvk.cotrip.data.sync

interface SyncStateStore {
    suspend fun getLastSync(): String?
    suspend fun setLastSync(value: String)
    suspend fun clear()
}
