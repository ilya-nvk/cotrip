package nvk.cotrip.backend.notifications

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nvk.cotrip.backend.db.NotificationRepository
import nvk.cotrip.backend.db.TripMemberRepository

private const val KEY_DISCUSSIONS_COMMENTS = "discussions_comments"
private const val KEY_EXPENSES_NEW = "expenses_new"
private const val KEY_EXPENSES_SETTLEMENTS = "expenses_settlements"
private const val TYPE_IDEA_COMMENT = "idea_comment"
private const val TYPE_IDEA_CREATED = "idea_created"
private const val TYPE_EXPENSE_CREATED = "expense_created"
private const val TYPE_EXPENSE_SETTLEMENT = "expense_settlement"
private val notificationJson = Json { encodeDefaults = true }

object NotificationService {
    suspend fun notifyIdeaCreated(
        tripId: String,
        ideaId: String,
        actorUserId: String,
        actorName: String,
        ideaTitle: String,
    ) {
        notifyMembers(
            tripId = tripId,
            actorUserId = actorUserId,
            settingKey = KEY_DISCUSSIONS_COMMENTS,
            type = TYPE_IDEA_CREATED,
            payload = notificationJson.encodeToString(
                buildJsonObject {
                    put("tripId", tripId)
                    put("ideaId", ideaId)
                    put("actorUserId", actorUserId)
                    put("actorName", actorName)
                    put("ideaTitle", ideaTitle.take(240))
                }
            )
        )
    }

    suspend fun notifyIdeaComment(
        tripId: String,
        ideaId: String,
        actorUserId: String,
        actorName: String,
        body: String,
    ) {
        notifyMembers(
            tripId = tripId,
            actorUserId = actorUserId,
            settingKey = KEY_DISCUSSIONS_COMMENTS,
            type = TYPE_IDEA_COMMENT,
            payload = notificationJson.encodeToString(
                buildJsonObject {
                    put("tripId", tripId)
                    put("ideaId", ideaId)
                    put("actorUserId", actorUserId)
                    put("actorName", actorName)
                    put("body", body.take(240))
                }
            )
        )
    }

    suspend fun notifyExpenseCreated(
        tripId: String,
        expenseId: String,
        actorUserId: String,
        actorName: String,
        title: String,
        amount: Double,
        currencyCode: String,
    ) {
        notifyMembers(
            tripId = tripId,
            actorUserId = actorUserId,
            settingKey = KEY_EXPENSES_NEW,
            type = TYPE_EXPENSE_CREATED,
            payload = notificationJson.encodeToString(
                buildJsonObject {
                    put("tripId", tripId)
                    put("expenseId", expenseId)
                    put("actorUserId", actorUserId)
                    put("actorName", actorName)
                    put("title", title.take(240))
                    put("amount", amount)
                    put("currencyCode", currencyCode)
                }
            )
        )
    }

    suspend fun notifyExpenseSettlement(
        tripId: String,
        expenseId: String,
        actorUserId: String,
        actorName: String,
        title: String,
    ) {
        notifyMembers(
            tripId = tripId,
            actorUserId = actorUserId,
            settingKey = KEY_EXPENSES_SETTLEMENTS,
            type = TYPE_EXPENSE_SETTLEMENT,
            payload = notificationJson.encodeToString(
                buildJsonObject {
                    put("tripId", tripId)
                    put("expenseId", expenseId)
                    put("actorUserId", actorUserId)
                    put("actorName", actorName)
                    put("title", title.take(240))
                }
            )
        )
    }

    private suspend fun notifyMembers(
        tripId: String,
        actorUserId: String,
        settingKey: String,
        type: String,
        payload: String,
    ) {
        val recipients = TripMemberRepository.listMemberIds(tripId).filter { it != actorUserId }
        if (recipients.isEmpty()) return

        recipients.forEach { userId ->
            if (NotificationRepository.isSettingEnabled(userId, settingKey)) {
                NotificationRepository.create(
                    userId = userId,
                    type = type,
                    payload = payload
                )
            }
        }
    }
}
