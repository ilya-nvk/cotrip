package nvk.cotrip.backend.notifications

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nvk.cotrip.backend.db.NotificationRepository
import nvk.cotrip.backend.db.TripMemberRepository

private const val KEY_DISCUSSIONS_COMMENTS = "discussions_comments"
private const val TYPE_IDEA_COMMENT = "idea_comment"
private val notificationJson = Json { encodeDefaults = true }

object NotificationService {
    fun notifyIdeaComment(
        tripId: String,
        ideaId: String,
        actorUserId: String,
        actorName: String,
        body: String,
    ) {
        val recipients = TripMemberRepository.listMemberIds(tripId)
            .filter { it != actorUserId }

        if (recipients.isEmpty()) return

        val payload = notificationJson.encodeToString(
            buildJsonObject {
                put("tripId", tripId)
                put("ideaId", ideaId)
                put("actorUserId", actorUserId)
                put("actorName", actorName)
                put("body", body.take(240))
            }
        )

        recipients.forEach { userId ->
            if (NotificationRepository.isSettingEnabled(userId, KEY_DISCUSSIONS_COMMENTS)) {
                NotificationRepository.create(
                    userId = userId,
                    type = TYPE_IDEA_COMMENT,
                    payload = payload
                )
            }
        }
    }
}
