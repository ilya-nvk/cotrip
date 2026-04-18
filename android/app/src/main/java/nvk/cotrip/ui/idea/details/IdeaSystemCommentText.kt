package nvk.cotrip.ui.idea.details

import android.content.Context
import nvk.cotrip.R
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.ws.CommentCreatedPayload

internal fun localizedSystemCommentText(
    context: Context,
    type: String,
    body: String,
    systemKey: String?,
    systemActorName: String?,
): String {
    if (!type.equals("system", ignoreCase = true)) return body
    val actor = systemActorName?.trim().orEmpty()
    return when (systemKey) {
        "idea_edited" -> context.getString(R.string.system_comment_idea_edited, actor)
        "idea_added_to_itinerary" ->
            context.getString(R.string.system_comment_idea_added_to_itinerary, actor)
        else -> legacyEnglishSystemBody(context, body)
    }
}

private fun legacyEnglishSystemBody(context: Context, body: String): String {
    val edited = Regex("^(.+) edited the idea$").find(body.trim())
    if (edited != null) {
        return context.getString(R.string.system_comment_idea_edited, edited.groupValues[1].trim())
    }
    val added = Regex("^(.+) added this idea to the itinerary$").find(body.trim())
    if (added != null) {
        return context.getString(
            R.string.system_comment_idea_added_to_itinerary,
            added.groupValues[1].trim(),
        )
    }
    return body
}

internal fun CommentDto.localizedSystemText(context: Context): String =
    localizedSystemCommentText(
        context = context,
        type = type,
        body = body,
        systemKey = systemKey,
        systemActorName = systemActorName,
    )

internal fun CommentCreatedPayload.localizedSystemText(context: Context): String =
    localizedSystemCommentText(
        context = context,
        type = type,
        body = body,
        systemKey = systemKey,
        systemActorName = systemActorName,
    )
