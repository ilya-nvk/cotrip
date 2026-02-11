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
import nvk.cotrip.data.network.dto.CommentDto
import javax.inject.Inject

class IdeaCommentsCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : IdeaCommentsCacheStore {

    override fun observeComments(ideaId: String): Flow<List<CommentDto>> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[COMMENTS_KEY]).byIdea[ideaId].orEmpty()
        }
    }

    override suspend fun getComments(ideaId: String): List<CommentDto> {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[COMMENTS_KEY]).byIdea[ideaId].orEmpty()
    }

    override suspend fun setComments(ideaId: String, comments: List<CommentDto>) {
        updateCache { cache ->
            cache.copy(byIdea = cache.byIdea.toMutableMap().apply { put(ideaId, comments) })
        }
    }

    override suspend fun clearIdea(ideaId: String) {
        updateCache { cache ->
            cache.copy(byIdea = cache.byIdea.toMutableMap().apply { remove(ideaId) })
        }
    }

    override suspend fun clearAll() {
        updateCache { cache -> cache.copy(byIdea = emptyMap()) }
    }

    private suspend fun updateCache(transform: (CommentsCache) -> CommentsCache) {
        dataStore.edit { prefs ->
            val current = decodeCache(prefs[COMMENTS_KEY])
            val updated = transform(current)
            prefs[COMMENTS_KEY] = json.encodeToString(CommentsCache.serializer(), updated)
        }
    }

    private fun decodeCache(raw: String?): CommentsCache {
        if (raw.isNullOrBlank()) return CommentsCache()
        return runCatching { json.decodeFromString(CommentsCache.serializer(), raw) }
            .getOrElse { CommentsCache() }
    }

    @Serializable
    private data class CommentsCache(
        val byIdea: Map<String, List<CommentDto>> = emptyMap(),
    )

    private companion object {
        private val COMMENTS_KEY = stringPreferencesKey("idea_comments_cache")
    }
}
