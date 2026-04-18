package nvk.cotrip.backend.comments

data class SystemCommentMetadata(
    val systemKey: String?,
    val systemActorName: String?,
)

object SystemCommentMetadataResolver {
    private val editedIdea = Regex("^(.*) edited the idea$")
    private val addedToItinerary = Regex("^(.*) added this idea to the itinerary$")

    fun resolve(type: String, body: String): SystemCommentMetadata {
        if (!type.equals("system", ignoreCase = true)) {
            return SystemCommentMetadata(systemKey = null, systemActorName = null)
        }
        editedIdea.matchEntire(body.trim())?.groupValues?.getOrNull(1)?.let { actor ->
            return SystemCommentMetadata(
                systemKey = "idea_edited",
                systemActorName = actor.trim().takeIf { it.isNotEmpty() },
            )
        }
        addedToItinerary.matchEntire(body.trim())?.groupValues?.getOrNull(1)?.let { actor ->
            return SystemCommentMetadata(
                systemKey = "idea_added_to_itinerary",
                systemActorName = actor.trim().takeIf { it.isNotEmpty() },
            )
        }
        return SystemCommentMetadata(systemKey = null, systemActorName = null)
    }
}
