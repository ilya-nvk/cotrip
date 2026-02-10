package nvk.cotrip.backend.db

import java.util.Locale
import java.util.UUID

data class LocalPlaceSuggestion(
    val name: String,
    val placeId: String,
    val fullText: String,
)

private data class LocalCandidate(
    val name: String,
    val sourceRank: Int,
)

object LocalPlacesSearchRepository {
    fun searchCities(
        tripId: String,
        query: String,
        limit: Int = 8,
    ): List<LocalPlaceSuggestion> {
        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isBlank()) return emptyList()
        val candidates = listCityCandidates(tripId)
        return rankCandidates(candidates, normalized, limit)
    }

    fun searchPlaces(
        tripId: String,
        query: String,
        limit: Int = 8,
    ): List<LocalPlaceSuggestion> {
        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isBlank()) return emptyList()
        val candidates = listPlaceCandidates(tripId)
        return rankCandidates(candidates, normalized, limit)
    }

    private fun rankCandidates(
        candidates: List<LocalCandidate>,
        normalizedQuery: String,
        limit: Int,
    ): List<LocalPlaceSuggestion> {
        return candidates
            .asSequence()
            .mapNotNull { candidate ->
                val name = candidate.name.trim()
                if (name.isBlank()) return@mapNotNull null
                val normalizedName = name.lowercase(Locale.getDefault())
                if (!normalizedName.contains(normalizedQuery)) return@mapNotNull null
                val matchRank = when {
                    normalizedName == normalizedQuery -> 0
                    normalizedName.startsWith(normalizedQuery) -> 1
                    else -> 2
                }
                RankedCandidate(
                    name = name,
                    normalizedName = normalizedName,
                    sourceRank = candidate.sourceRank,
                    matchRank = matchRank,
                )
            }
            .sortedWith(
                compareBy<RankedCandidate> { it.matchRank }
                    .thenBy { it.sourceRank }
                    .thenBy { it.name.length }
                    .thenBy { it.name }
            )
            .distinctBy { it.normalizedName }
            .take(limit.coerceIn(1, 20))
            .map { ranked ->
                LocalPlaceSuggestion(
                    name = ranked.name,
                    placeId = buildLocalPlaceId(ranked.name),
                    fullText = ranked.name,
                )
            }
            .toList()
    }

    private fun listCityCandidates(tripId: String): List<LocalCandidate> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT city AS name, 1 AS source_rank
            FROM itinerary_days
            WHERE trip_id = ? AND city IS NOT NULL AND btrim(city) <> ''
            UNION ALL
            SELECT city AS name, 2 AS source_rank
            FROM ideas
            WHERE trip_id = ? AND deleted_at IS NULL AND city IS NOT NULL AND btrim(city) <> ''
            UNION ALL
            SELECT split_part(location_line, ',', 1) AS name, 3 AS source_rank
            FROM trips
            WHERE id = ? AND location_line IS NOT NULL AND btrim(location_line) <> '' AND deleted_at IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(tripId))
            stmt.setObject(3, UUID.fromString(tripId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<LocalCandidate>()
                while (rs.next()) {
                    result += LocalCandidate(
                        name = rs.getString("name"),
                        sourceRank = rs.getInt("source_rank"),
                    )
                }
                result
            }
        }
    }

    private fun listPlaceCandidates(tripId: String): List<LocalCandidate> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT a.location_name AS name, 1 AS source_rank
            FROM activities a
            JOIN itinerary_days d ON d.id = a.day_id
            WHERE d.trip_id = ? AND a.deleted_at IS NULL
              AND a.location_name IS NOT NULL AND btrim(a.location_name) <> ''
            UNION ALL
            SELECT i.title AS name, 2 AS source_rank
            FROM ideas i
            WHERE i.trip_id = ? AND i.deleted_at IS NULL
              AND i.title IS NOT NULL AND btrim(i.title) <> ''
            UNION ALL
            SELECT i.city AS name, 3 AS source_rank
            FROM ideas i
            WHERE i.trip_id = ? AND i.deleted_at IS NULL
              AND i.city IS NOT NULL AND btrim(i.city) <> ''
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setObject(2, UUID.fromString(tripId))
            stmt.setObject(3, UUID.fromString(tripId))
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<LocalCandidate>()
                while (rs.next()) {
                    result += LocalCandidate(
                        name = rs.getString("name"),
                        sourceRank = rs.getInt("source_rank"),
                    )
                }
                result
            }
        }
    }

    private fun buildLocalPlaceId(name: String): String {
        val slug = name
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "place" }
        val hash = name.hashCode().toUInt().toString(16)
        return "local:$slug:$hash"
    }
}

private data class RankedCandidate(
    val name: String,
    val normalizedName: String,
    val sourceRank: Int,
    val matchRank: Int,
)
