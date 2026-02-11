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
import nvk.cotrip.data.network.dto.IdeaDto
import javax.inject.Inject

class IdeasCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : IdeasCacheStore {

    override fun observeIdeas(tripId: String): Flow<List<IdeaDto>> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[IDEAS_KEY]).byTrip[tripId].orEmpty()
        }
    }

    override fun observeIdeaById(ideaId: String): Flow<IdeaDto?> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[IDEAS_KEY]).byTrip.values
                .asSequence()
                .flatten()
                .firstOrNull { it.id == ideaId }
        }
    }

    override suspend fun getIdeas(tripId: String): List<IdeaDto> {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[IDEAS_KEY]).byTrip[tripId].orEmpty()
    }

    override suspend fun findIdeaById(ideaId: String): IdeaDto? {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[IDEAS_KEY]).byTrip.values
            .asSequence()
            .flatten()
            .firstOrNull { it.id == ideaId }
    }

    override suspend fun setIdeas(tripId: String, ideas: List<IdeaDto>) {
        updateCache { cache ->
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, ideas) })
        }
    }

    override suspend fun upsertIdea(tripId: String, idea: IdeaDto) {
        updateCache { cache ->
            val existing = cache.byTrip[tripId].orEmpty().toMutableList()
            val index = existing.indexOfFirst { it.id == idea.id }
            if (index >= 0) {
                existing[index] = idea
            } else {
                existing.add(0, idea)
            }
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, existing) })
        }
    }

    override suspend fun removeIdea(tripId: String, ideaId: String) {
        updateCache { cache ->
            val remaining = cache.byTrip[tripId].orEmpty().filterNot { it.id == ideaId }
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, remaining) })
        }
    }

    override suspend fun clearTrip(tripId: String) {
        updateCache { cache ->
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { remove(tripId) })
        }
    }

    override suspend fun clearAll() {
        updateCache { cache -> cache.copy(byTrip = emptyMap()) }
    }

    private suspend fun updateCache(transform: (IdeasCache) -> IdeasCache) {
        dataStore.edit { prefs ->
            val current = decodeCache(prefs[IDEAS_KEY])
            val updated = transform(current)
            prefs[IDEAS_KEY] = json.encodeToString(IdeasCache.serializer(), updated)
        }
    }

    private fun decodeCache(raw: String?): IdeasCache {
        if (raw.isNullOrBlank()) return IdeasCache()
        return runCatching { json.decodeFromString(IdeasCache.serializer(), raw) }
            .getOrElse { IdeasCache() }
    }

    @Serializable
    private data class IdeasCache(
        val byTrip: Map<String, List<IdeaDto>> = emptyMap(),
    )

    private companion object {
        private val IDEAS_KEY = stringPreferencesKey("ideas_cache")
    }
}
