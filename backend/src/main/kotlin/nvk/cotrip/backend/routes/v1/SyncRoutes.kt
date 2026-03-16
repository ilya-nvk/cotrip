package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import nvk.cotrip.backend.db.ActivityRepository
import nvk.cotrip.backend.db.DayRepository
import nvk.cotrip.backend.db.ExpenseParticipantRow
import nvk.cotrip.backend.db.ExpenseRepository
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.ItineraryDayRepository
import nvk.cotrip.backend.db.SyncRepository
import nvk.cotrip.backend.db.TripMemberRepository
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.TripUpdate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Serializable
data class SyncPushItem(
    val entity: String,
    val id: String,
    val type: String,
    val payload: JsonElement = JsonNull,
)

@Serializable
data class SyncPushRequest(
    val items: List<SyncPushItem> = emptyList(),
)

@Serializable
data class SyncConflict(
    val id: String,
    val reason: String,
)

@Serializable
data class SyncPullResponse(
    val items: List<SyncChangeDto>,
    val nextCursor: String? = null,
)

@Serializable
data class SyncPushResponse(
    val applied: List<String>,
    val conflicts: List<SyncConflict>,
)

@Serializable
private data class SyncTripUpsertPayload(
    val title: String? = null,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String? = null,
    val status: String? = null,
)

private const val OP_UPSERT = "upsert"
private const val OP_DELETE = "delete"
private const val OP_CREATE = "create"

private const val REASON_INVALID_PAYLOAD = "invalid_payload"
private const val REASON_FORBIDDEN = "forbidden"
private const val REASON_NOT_FOUND = "not_found"
private const val REASON_UNSUPPORTED_OPERATION = "unsupported_operation"
private const val REASON_UNSUPPORTED_ENTITY = "unsupported_entity"
private const val REASON_INTERNAL_ERROR = "internal_error"

private val syncJson = Json { ignoreUnknownKeys = true }

private class SyncApplyException(val reason: String) : RuntimeException(reason)

fun Route.syncRoutes() {
    authenticate("auth-jwt") {
        get("/v1/sync/changes") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val sinceParam = call.request.queryParameters["since"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val since = runCatching { OffsetDateTime.parse(sinceParam) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100)
            val cursor = call.request.queryParameters["cursor"]

            if (limit == null && cursor.isNullOrBlank()) {
                val changes = SyncRepository.listChanges(userId, since).map { change ->
                    SyncChangeDto(
                        entity = change.entity,
                        id = change.id,
                        updatedAt = change.updatedAt.toString(),
                        deletedAt = change.deletedAt?.toString(),
                        payload = change.payload,
                    )
                }
                call.respond(SyncPullResponse(items = changes, nextCursor = null))
            } else {
                val page = SyncRepository.listChangesPage(
                    userId = userId,
                    since = since,
                    limit = limit ?: 100,
                    cursor = cursor,
                )
                val items = page.items.map { change ->
                    SyncChangeDto(
                        entity = change.entity,
                        id = change.id,
                        updatedAt = change.updatedAt.toString(),
                        deletedAt = change.deletedAt?.toString(),
                        payload = change.payload,
                    )
                }
                call.respond(SyncPullResponse(items = items, nextCursor = page.nextCursor))
            }
        }

        post("/v1/sync/changes") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = call.receive<SyncPushRequest>()
            val applied = mutableListOf<String>()
            val conflicts = mutableListOf<SyncConflict>()

            request.items.forEach { item ->
                val reason = runCatching {
                    applySyncItem(userId = userId, item = item)
                    null
                }.getOrElse { error ->
                    when (error) {
                        is SyncApplyException -> error.reason
                        else -> REASON_INTERNAL_ERROR
                    }
                }

                if (reason == null) {
                    applied += item.id
                } else {
                    conflicts += SyncConflict(
                        id = item.id,
                        reason = reason,
                    )
                }
            }

            call.respond(SyncPushResponse(applied = applied, conflicts = conflicts))
        }
    }
}

private fun applySyncItem(userId: String, item: SyncPushItem) {
    val operation = item.type.trim().lowercase()
    if (operation == OP_CREATE) {
        throw SyncApplyException(REASON_UNSUPPORTED_OPERATION)
    }

    when (operation) {
        OP_UPSERT -> applyUpsert(userId = userId, item = item)
        OP_DELETE -> applyDelete(userId = userId, item = item)
        else -> throw SyncApplyException(REASON_UNSUPPORTED_OPERATION)
    }
}

private fun applyUpsert(userId: String, item: SyncPushItem) {
    when (normalizeEntity(item.entity)) {
        "trip" -> applyTripUpsert(userId = userId, item = item)
        "idea" -> applyIdeaUpsert(userId = userId, item = item)
        "itinerary_day" -> applyDayUpsert(userId = userId, item = item)
        "activity" -> applyActivityUpsert(userId = userId, item = item)
        "expense" -> applyExpenseUpsert(userId = userId, item = item)
        else -> throw SyncApplyException(REASON_UNSUPPORTED_ENTITY)
    }
}

private fun applyDelete(userId: String, item: SyncPushItem) {
    when (normalizeEntity(item.entity)) {
        "trip" -> applyTripDelete(userId = userId, item = item)
        "idea" -> applyIdeaDelete(userId = userId, item = item)
        "activity" -> applyActivityDelete(userId = userId, item = item)
        "expense" -> applyExpenseDelete(userId = userId, item = item)
        "itinerary_day" -> throw SyncApplyException(REASON_UNSUPPORTED_OPERATION)
        else -> throw SyncApplyException(REASON_UNSUPPORTED_ENTITY)
    }
}

private fun applyTripUpsert(userId: String, item: SyncPushItem) {
    val tripId = normalizeUuid(item.id)
    val payload = decodePayload<SyncTripUpsertPayload>(item.payload)

    val existingTrip = TripRepository.getTripById(tripId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (!TripRepository.isOwner(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val status = payload.status?.trim()?.lowercase()
    if (status != null) {
        if (status != "archived") {
            throw SyncApplyException(REASON_INVALID_PAYLOAD)
        }
        val archived = TripRepository.archiveTrip(ownerId = userId, tripId = tripId)
        if (!archived) {
            throw SyncApplyException(REASON_FORBIDDEN)
        }
        return
    }

    val parsedStartDate = payload.startDate?.let(::parseDate)
    val parsedEndDate = payload.endDate?.let(::parseDate)
    val effectiveStart = parsedStartDate ?: existingTrip.startDate
    val effectiveEnd = parsedEndDate ?: existingTrip.endDate
    if (effectiveEnd.isBefore(effectiveStart)) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val updated = TripRepository.updateTrip(
        ownerId = userId,
        tripId = tripId,
        update = TripUpdate(
            title = payload.title?.trim()?.takeIf { it.isNotBlank() },
            description = payload.description?.trim()?.takeIf { it.isNotBlank() },
            startDate = parsedStartDate,
            endDate = parsedEndDate,
            locationLine = payload.locationLine?.trim()?.takeIf { it.isNotBlank() },
            coverUrl = payload.coverUrl?.trim()?.takeIf { it.isNotBlank() },
            currencyCode = payload.currencyCode?.trim()?.takeIf { it.isNotBlank() },
        )
    )
    if (updated == null) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
}

private fun applyTripDelete(userId: String, item: SyncPushItem) {
    val tripId = normalizeUuid(item.id)
    val existing = TripRepository.getTripById(tripId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (existing.ownerId != userId) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    val deleted = TripRepository.deleteTrip(ownerId = userId, tripId = tripId)
    if (!deleted) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
}

private fun applyIdeaUpsert(userId: String, item: SyncPushItem) {
    val ideaId = normalizeUuid(item.id)
    val existing = IdeaRepository.get(ideaId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    val canEdit = TripRepository.isOwner(existing.tripId, userId) || existing.authorId == userId
    if (!canEdit) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val payload = decodePayload<UpdateIdeaRequest>(item.payload)
    val updated = IdeaRepository.update(
        ideaId = ideaId,
        title = payload.title,
        city = payload.city,
        link = payload.link,
        costAmount = payload.costAmount,
        costType = payload.costType,
        notes = payload.notes,
    )
    if (updated == null) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyIdeaDelete(userId: String, item: SyncPushItem) {
    val ideaId = normalizeUuid(item.id)
    val existing = IdeaRepository.get(ideaId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    val canDelete = TripRepository.isOwner(existing.tripId, userId) || existing.authorId == userId
    if (!canDelete) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    val deleted = IdeaRepository.softDelete(ideaId)
    if (!deleted) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyDayUpsert(userId: String, item: SyncPushItem) {
    val dayId = normalizeUuid(item.id)
    val tripId = DayRepository.findTripIdByDayId(dayId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (!TripRepository.isMember(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val payload = decodePayload<UpdateDayRequest>(item.payload)
    val city = payload.city?.trim()?.ifBlank { null }
    val cityProviderId = payload.cityProviderId?.trim()?.ifBlank { null }
    val updated = if (city == null) {
        ItineraryDayRepository.updateCity(
            dayId = dayId,
            city = null,
            cityProviderId = null,
            cityLat = null,
            cityLon = null,
        )
    } else {
        if (payload.cityLat == null || payload.cityLon == null) {
            throw SyncApplyException(REASON_INVALID_PAYLOAD)
        }
        ItineraryDayRepository.updateCity(
            dayId = dayId,
            city = city,
            cityProviderId = cityProviderId,
            cityLat = payload.cityLat,
            cityLon = payload.cityLon,
        )
    }

    if (updated == null) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyActivityUpsert(userId: String, item: SyncPushItem) {
    val payloadObject = payloadAsObject(item.payload)
    if ("dayId" in payloadObject) {
        val movePayload = decodePayload<MoveActivityRequest>(item.payload)
        applyActivityMove(userId = userId, activityIdRaw = item.id, payload = movePayload)
        return
    }

    val activityId = normalizeUuid(item.id)
    val existing = ActivityRepository.get(activityId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    val tripId = DayRepository.findTripIdByDayId(existing.dayId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (!TripRepository.isMember(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val payload = decodePayload<UpdateActivityRequest>(item.payload)
    val updated = ActivityRepository.update(
        activityId = activityId,
        title = payload.title,
        timeText = payload.timeText,
        locationName = payload.locationName,
        link = payload.link,
        costAmount = payload.costAmount,
        costType = payload.costType,
        notes = payload.notes,
    )
    if (updated == null) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyActivityMove(userId: String, activityIdRaw: String, payload: MoveActivityRequest) {
    val activityId = normalizeUuid(activityIdRaw)
    val existing = ActivityRepository.get(activityId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    val sourceTripId = DayRepository.findTripIdByDayId(existing.dayId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (!TripRepository.isMember(sourceTripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val targetDayId = normalizeUuid(payload.dayId)
    val targetTripId = DayRepository.findTripIdByDayId(targetDayId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (targetTripId != sourceTripId) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val orderIndex = payload.orderIndex ?: ActivityRepository.nextOrderIndex(targetDayId)
    val moved = ActivityRepository.move(
        activityId = activityId,
        dayId = targetDayId,
        orderIndex = orderIndex,
    )
    if (moved == null) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyActivityDelete(userId: String, item: SyncPushItem) {
    val activityId = normalizeUuid(item.id)
    val existing = ActivityRepository.get(activityId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    val tripId = DayRepository.findTripIdByDayId(existing.dayId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (!TripRepository.isMember(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    val deleted = ActivityRepository.softDelete(activityId)
    if (!deleted) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyExpenseUpsert(userId: String, item: SyncPushItem) {
    val expenseId = normalizeUuid(item.id)
    val existing = ExpenseRepository.get(expenseId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (!TripRepository.isMember(existing.tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val payload = decodePayload<ExpenseUpdateRequest>(item.payload)
    if (payload.status != null && !isValidExpenseStatus(payload.status)) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
    if (payload.splitType != null && !isValidSplitType(payload.splitType)) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val memberIds = TripMemberRepository.listMemberIds(existing.tripId)
    val paidById = payload.paidById?.let(::normalizeUuid)
    if (paidById != null && paidById !in memberIds) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val participants = payload.participants?.let { items ->
        if (items.isEmpty()) {
            throw SyncApplyException(REASON_INVALID_PAYLOAD)
        }
        items.map { participant ->
            val participantId = normalizeUuid(participant.userId)
            if (participantId !in memberIds) {
                throw SyncApplyException(REASON_INVALID_PAYLOAD)
            }
            ExpenseParticipantRow(
                expenseId = expenseId,
                userId = participantId,
                shareAmount = participant.shareAmount,
                isIncluded = participant.isIncluded,
                isPaid = participant.isPaid,
            )
        }
    }

    val updated = ExpenseRepository.update(
        expenseId = expenseId,
        title = payload.title,
        amount = payload.amount,
        status = payload.status,
        paidById = paidById,
        expenseDate = payload.date?.let(::parseDate),
        splitType = payload.splitType,
        note = payload.note,
        participants = participants,
    )
    if (updated == null) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyExpenseDelete(userId: String, item: SyncPushItem) {
    val expenseId = normalizeUuid(item.id)
    val existing = ExpenseRepository.get(expenseId)
        ?: throw SyncApplyException(REASON_NOT_FOUND)
    if (!TripRepository.isMember(existing.tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    val deleted = ExpenseRepository.softDelete(expenseId)
    if (!deleted) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun normalizeEntity(raw: String): String {
    return when (raw.trim().lowercase()) {
        "day" -> "itinerary_day"
        "itinerary_day" -> "itinerary_day"
        else -> raw.trim().lowercase()
    }
}

private fun normalizeUuid(raw: String): String {
    return runCatching { UUID.fromString(raw.trim()).toString() }
        .getOrElse { throw SyncApplyException(REASON_INVALID_PAYLOAD) }
}

private fun parseDate(raw: String): LocalDate {
    return runCatching { LocalDate.parse(raw.trim()) }
        .getOrElse { throw SyncApplyException(REASON_INVALID_PAYLOAD) }
}

private inline fun <reified T> decodePayload(payload: JsonElement): T {
    val obj = payloadAsObject(payload)
    return runCatching { syncJson.decodeFromJsonElement<T>(obj) }
        .getOrElse { throw SyncApplyException(REASON_INVALID_PAYLOAD) }
}

private fun payloadAsObject(payload: JsonElement): JsonObject {
    return payload as? JsonObject ?: throw SyncApplyException(REASON_INVALID_PAYLOAD)
}

private fun isValidExpenseStatus(status: String): Boolean {
    return status == "planned" || status == "paid"
}

private fun isValidSplitType(splitType: String): Boolean {
    return splitType == "equally" || splitType == "custom"
}
