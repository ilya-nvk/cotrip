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
import nvk.cotrip.backend.db.AiRepository
import nvk.cotrip.backend.db.DayRepository
import nvk.cotrip.backend.db.ExpenseParticipantRow
import nvk.cotrip.backend.db.ExpenseRepository
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.ItineraryDayRepository
import nvk.cotrip.backend.db.NotificationRepository
import nvk.cotrip.backend.db.NotificationSettingRow
import nvk.cotrip.backend.db.SyncRepository
import nvk.cotrip.backend.db.TripMemberRepository
import nvk.cotrip.backend.db.TripDaySeed
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.TripUpdate
import nvk.cotrip.backend.db.UserRepository
import nvk.cotrip.backend.limits.LimitReachedException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Serializable
data class SyncPushItem(
    val changeId: String? = null,
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
    val changeId: String,
    val entityId: String,
    val reason: String,
    val retryable: Boolean = false,
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

@Serializable
private data class SyncTripCreateDayPayload(
    val id: String,
    val date: String,
    val dayNumber: Int,
)

@Serializable
private data class SyncTripCreatePayload(
    val title: String,
    val description: String? = null,
    val startDate: String,
    val endDate: String,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String,
    val days: List<SyncTripCreateDayPayload> = emptyList(),
)

@Serializable
private data class SyncIdeaCreatePayload(
    val tripId: String,
    val title: String,
    val city: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
)

@Serializable
private data class SyncExpenseCreatePayload(
    val tripId: String,
    val title: String,
    val amount: Double,
    val currencyCode: String? = null,
    val status: String,
    val paidById: String? = null,
    val date: String? = null,
    val splitType: String,
    val note: String? = null,
    val participants: List<ExpenseParticipantInput> = emptyList(),
)

@Serializable
private data class SyncActivityCreatePayload(
    val dayId: String,
    val title: String,
    val timeText: String? = null,
    val locationName: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
    val orderIndex: Int? = null,
)

@Serializable
private data class SyncTripMemberDeletePayload(
    val tripId: String,
    val memberId: String,
)

@Serializable
private data class SyncIdeaStatusUpsertPayload(
    val status: String,
)

@Serializable
private data class SyncIdeaConvertCreatePayload(
    val dayId: String,
    val timeText: String? = null,
    val orderIndex: Int? = null,
)

@Serializable
private data class SyncActivityReorderUpsertPayload(
    val dayId: String,
    val orderedIds: List<String> = emptyList(),
)

@Serializable
private data class SyncItineraryTrimUpsertPayload(
    val tripId: String,
    val action: String,
    val dayIds: List<String>,
)

@Serializable
private data class SyncNotificationSettingsUpsertPayload(
    val items: List<NotificationSettingDto> = emptyList(),
)

@Serializable
private data class SyncNotificationReadUpsertPayload(
    val mode: String,
    val notificationId: String? = null,
    val ideaId: String? = null,
)

@Serializable
private data class SyncUserProfileUpsertPayload(
    val name: String,
    val photoUrl: String? = null,
)

@Serializable
private data class SyncAiSuggestionSaveUpsertPayload(
    val suggestionId: String? = null,
)

private const val OP_UPSERT = "upsert"
private const val OP_DELETE = "delete"
private const val OP_CREATE = "create"

private const val REASON_INVALID_PAYLOAD = "invalid_payload"
private const val REASON_FORBIDDEN = "forbidden"
private const val REASON_NOT_FOUND = "not_found"
private const val REASON_LIMIT_REACHED = "limit_reached"
private const val REASON_DEPENDENCY_NOT_READY = "dependency_not_ready"
private const val REASON_UNSUPPORTED_OPERATION = "unsupported_operation"
private const val REASON_UNSUPPORTED_ENTITY = "unsupported_entity"
private const val REASON_INTERNAL_ERROR = "internal_error"

private const val READ_BULK_MODE_NON_COMMENT = "non_comment"
private const val READ_BULK_MODE_IDEA_COMMENTS = "idea_comments"

private val syncJson = Json { ignoreUnknownKeys = true }

private class SyncApplyException(
    val reason: String,
    val retryable: Boolean = false,
) : RuntimeException(reason)

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
                val operationId = operationId(item)
                val conflict = runCatching {
                    applySyncItem(userId = userId, item = item)
                    null
                }.getOrElse { error ->
                    when (error) {
                        is SyncApplyException -> SyncConflict(
                            changeId = operationId,
                            entityId = item.id,
                            reason = error.reason,
                            retryable = error.retryable,
                        )
                        is LimitReachedException -> SyncConflict(
                            changeId = operationId,
                            entityId = item.id,
                            reason = REASON_LIMIT_REACHED,
                            retryable = false,
                        )
                        else -> SyncConflict(
                            changeId = operationId,
                            entityId = item.id,
                            reason = REASON_INTERNAL_ERROR,
                            retryable = true,
                        )
                    }
                }

                if (conflict == null) {
                    applied += operationId
                } else {
                    conflicts += conflict
                }
            }

            call.respond(SyncPushResponse(applied = applied, conflicts = conflicts))
        }
    }
}

private fun applySyncItem(userId: String, item: SyncPushItem) {
    val operation = item.type.trim().lowercase()
    when (operation) {
        OP_CREATE -> applyCreate(userId = userId, item = item)
        OP_UPSERT -> applyUpsert(userId = userId, item = item)
        OP_DELETE -> applyDelete(userId = userId, item = item)
        else -> throw SyncApplyException(REASON_UNSUPPORTED_OPERATION)
    }
}

private fun applyCreate(userId: String, item: SyncPushItem) {
    when (normalizeEntity(item.entity)) {
        "trip" -> applyTripCreate(userId = userId, item = item)
        "idea" -> applyIdeaCreate(userId = userId, item = item)
        "activity" -> applyActivityCreate(userId = userId, item = item)
        "expense" -> applyExpenseCreate(userId = userId, item = item)
        "idea_convert" -> applyIdeaConvertCreate(userId = userId, item = item)
        "itinerary_day" -> throw SyncApplyException(REASON_UNSUPPORTED_OPERATION)
        else -> throw SyncApplyException(REASON_UNSUPPORTED_ENTITY)
    }
}

private fun applyUpsert(userId: String, item: SyncPushItem) {
    when (normalizeEntity(item.entity)) {
        "trip" -> applyTripUpsert(userId = userId, item = item)
        "idea" -> applyIdeaUpsert(userId = userId, item = item)
        "itinerary_day" -> applyDayUpsert(userId = userId, item = item)
        "activity" -> applyActivityUpsert(userId = userId, item = item)
        "expense" -> applyExpenseUpsert(userId = userId, item = item)
        "idea_status" -> applyIdeaStatusUpsert(userId = userId, item = item)
        "activity_reorder" -> applyActivityReorderUpsert(userId = userId, item = item)
        "itinerary_trim" -> applyItineraryTrimUpsert(userId = userId, item = item)
        "notification_settings" -> applyNotificationSettingsUpsert(userId = userId, item = item)
        "notification_read" -> applyNotificationReadUpsert(userId = userId, item = item)
        "user_profile" -> applyUserProfileUpsert(userId = userId, item = item)
        "ai_suggestion_save" -> applyAiSuggestionSaveUpsert(userId = userId, item = item)
        else -> throw SyncApplyException(REASON_UNSUPPORTED_ENTITY)
    }
}

private fun applyDelete(userId: String, item: SyncPushItem) {
    when (normalizeEntity(item.entity)) {
        "trip" -> applyTripDelete(userId = userId, item = item)
        "idea" -> applyIdeaDelete(userId = userId, item = item)
        "activity" -> applyActivityDelete(userId = userId, item = item)
        "expense" -> applyExpenseDelete(userId = userId, item = item)
        "trip_member" -> applyTripMemberDelete(userId = userId, item = item)
        "itinerary_day" -> throw SyncApplyException(REASON_UNSUPPORTED_OPERATION)
        else -> throw SyncApplyException(REASON_UNSUPPORTED_ENTITY)
    }
}

private fun applyTripCreate(userId: String, item: SyncPushItem) {
    val tripId = normalizeUuid(item.id)
    val existingTrip = TripRepository.getTripById(tripId)
    if (existingTrip != null) {
        if (existingTrip.ownerId != userId) {
            throw SyncApplyException(REASON_FORBIDDEN)
        }
        return
    }

    val payload = decodePayload<SyncTripCreatePayload>(item.payload)
    val title = payload.title.trim()
    if (title.isBlank()) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
    val currencyCode = payload.currencyCode.trim()
    if (currencyCode.isBlank()) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
    val startDate = parseDate(payload.startDate)
    val endDate = parseDate(payload.endDate)
    if (endDate.isBefore(startDate)) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
    if (payload.days.isEmpty()) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val daySeeds = payload.days.map { day ->
        val dayId = normalizeUuid(day.id)
        if (day.dayNumber <= 0) {
            throw SyncApplyException(REASON_INVALID_PAYLOAD)
        }
        TripDaySeed(
            id = dayId,
            date = parseDate(day.date),
            dayNumber = day.dayNumber,
        )
    }

    val uniqueDayIds = daySeeds.map { it.id }.toSet()
    val uniqueDayNumbers = daySeeds.map { it.dayNumber }.toSet()
    val uniqueDayDates = daySeeds.map { it.date }.toSet()
    if (
        uniqueDayIds.size != daySeeds.size ||
        uniqueDayNumbers.size != daySeeds.size ||
        uniqueDayDates.size != daySeeds.size
    ) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
    val isContiguousDayNumbering = daySeeds
        .sortedBy { it.dayNumber }
        .withIndex()
        .all { (index, day) -> day.dayNumber == index + 1 }
    if (!isContiguousDayNumbering) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    TripRepository.createTrip(
        ownerId = userId,
        title = title,
        description = payload.description?.trim()?.takeIf { it.isNotBlank() },
        startDate = startDate,
        endDate = endDate,
        locationLine = payload.locationLine?.trim()?.takeIf { it.isNotBlank() },
        coverUrl = payload.coverUrl?.trim()?.takeIf { it.isNotBlank() },
        currencyCode = currencyCode,
        tripId = tripId,
        daySeeds = daySeeds,
    )
}

private fun applyIdeaCreate(userId: String, item: SyncPushItem) {
    val ideaId = normalizeUuid(item.id)
    val existing = IdeaRepository.get(ideaId)
    if (existing != null) {
        val canAccess = existing.authorId == userId || TripRepository.isOwner(existing.tripId, userId)
        if (!canAccess) {
            throw SyncApplyException(REASON_FORBIDDEN)
        }
        return
    }

    val payload = decodePayload<SyncIdeaCreatePayload>(item.payload)
    val tripId = normalizeUuid(payload.tripId)
    val trip = TripRepository.getTripById(tripId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (!TripRepository.isMember(trip.id, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    IdeaRepository.create(
        tripId = tripId,
        authorId = userId,
        title = payload.title.trim(),
        city = payload.city?.trim()?.takeIf { it.isNotBlank() },
        link = payload.link?.trim()?.takeIf { it.isNotBlank() },
        costAmount = payload.costAmount,
        costType = payload.costType?.trim()?.takeIf { it.isNotBlank() },
        notes = payload.notes?.trim()?.takeIf { it.isNotBlank() },
        ideaId = ideaId,
    )
}

private fun applyActivityCreate(userId: String, item: SyncPushItem) {
    val activityId = normalizeUuid(item.id)
    val existing = ActivityRepository.get(activityId)
    if (existing != null) {
        val tripId = DayRepository.findTripIdByDayId(existing.dayId)
            ?: throw SyncApplyException(REASON_NOT_FOUND)
        if (!TripRepository.isMember(tripId, userId)) {
            throw SyncApplyException(REASON_FORBIDDEN)
        }
        return
    }

    val payload = decodePayload<SyncActivityCreatePayload>(item.payload)
    val dayId = normalizeUuid(payload.dayId)
    val tripId = DayRepository.findTripIdByDayId(dayId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (!TripRepository.isMember(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val orderIndex = payload.orderIndex ?: ActivityRepository.nextOrderIndex(dayId)
    ActivityRepository.create(
        dayId = dayId,
        title = payload.title.trim(),
        timeText = payload.timeText?.trim()?.takeIf { it.isNotBlank() },
        locationName = payload.locationName?.trim()?.takeIf { it.isNotBlank() },
        link = payload.link?.trim()?.takeIf { it.isNotBlank() },
        costAmount = payload.costAmount,
        costType = payload.costType?.trim()?.takeIf { it.isNotBlank() },
        notes = payload.notes?.trim()?.takeIf { it.isNotBlank() },
        orderIndex = orderIndex,
        activityId = activityId,
    )
}

private fun applyExpenseCreate(userId: String, item: SyncPushItem) {
    val expenseId = normalizeUuid(item.id)
    val existing = ExpenseRepository.get(expenseId)
    if (existing != null) {
        if (!TripRepository.isMember(existing.tripId, userId)) {
            throw SyncApplyException(REASON_FORBIDDEN)
        }
        return
    }

    val payload = decodePayload<SyncExpenseCreatePayload>(item.payload)
    val tripId = normalizeUuid(payload.tripId)
    val trip = TripRepository.getTripById(tripId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (!TripRepository.isMember(trip.id, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    val status = payload.status.trim().lowercase()
    val splitType = payload.splitType.trim().lowercase()
    if (!isValidExpenseStatus(status) || !isValidSplitType(splitType)) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
    if (payload.participants.isEmpty()) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val memberIds = TripMemberRepository.listMemberIds(tripId)
    val paidById = payload.paidById?.let(::normalizeUuid)
    if (paidById != null && paidById !in memberIds) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val participants = payload.participants.map { participant ->
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

    val currencyCode = payload.currencyCode?.trim()?.takeIf { it.isNotBlank() } ?: trip.currencyCode
    if (currencyCode != trip.currencyCode) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    ExpenseRepository.create(
        tripId = tripId,
        title = payload.title.trim(),
        amount = payload.amount,
        currencyCode = currencyCode,
        status = status,
        paidById = paidById,
        expenseDate = payload.date?.let(::parseDate),
        splitType = splitType,
        note = payload.note?.trim()?.takeIf { it.isNotBlank() },
        participants = participants,
        expenseId = expenseId,
    )
}

private fun applyTripUpsert(userId: String, item: SyncPushItem) {
    val tripId = normalizeUuid(item.id)
    val payload = decodePayload<SyncTripUpsertPayload>(item.payload)

    val existingTrip = TripRepository.getTripById(tripId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
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
    val existing = TripRepository.getTripById(tripId) ?: return
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
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
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
    val existing = IdeaRepository.get(ideaId) ?: return
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
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
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
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
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
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
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
    val existing = ActivityRepository.get(activityId) ?: return
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
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
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
    val existing = ExpenseRepository.get(expenseId) ?: return
    if (!TripRepository.isMember(existing.tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    val deleted = ExpenseRepository.softDelete(expenseId)
    if (!deleted) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyTripMemberDelete(userId: String, item: SyncPushItem) {
    val payload = decodePayload<SyncTripMemberDeletePayload>(item.payload)
    val tripId = normalizeUuid(payload.tripId)
    val memberId = normalizeUuid(payload.memberId)

    if (!TripRepository.isMember(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val target = TripMemberRepository.findMember(tripId, memberId) ?: return
    if (target.role == "owner") {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val isOwner = TripRepository.isOwner(tripId, userId)
    if (!isOwner && userId != memberId) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val removed = TripMemberRepository.removeMember(tripId, memberId)
    if (!removed) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyIdeaStatusUpsert(userId: String, item: SyncPushItem) {
    val ideaId = normalizeUuid(item.id)
    val payload = decodePayload<SyncIdeaStatusUpsertPayload>(item.payload)
    val status = payload.status.trim().lowercase()
    if (status != "approved" && status != "rejected") {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val existing = IdeaRepository.get(ideaId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (!TripRepository.isOwner(existing.tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    if (existing.status == status) {
        return
    }
    val updated = IdeaRepository.updateStatus(ideaId, status)
    if (updated == null) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyIdeaConvertCreate(userId: String, item: SyncPushItem) {
    val ideaId = normalizeUuid(item.id)
    val idea = IdeaRepository.get(ideaId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (!TripRepository.isMember(idea.tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val payload = decodePayload<SyncIdeaConvertCreatePayload>(item.payload)
    val dayId = normalizeUuid(payload.dayId)
    val dayTripId = DayRepository.findTripIdByDayId(dayId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (dayTripId != idea.tripId) {
        throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }

    val existingActivity = ActivityRepository.findBySourceIdea(dayId = dayId, sourceIdeaId = ideaId)
    if (existingActivity != null) {
        return
    }

    val orderIndex = payload.orderIndex ?: ActivityRepository.nextOrderIndex(dayId)
    ActivityRepository.createFromIdea(
        dayId = dayId,
        idea = idea,
        timeText = payload.timeText,
        orderIndex = orderIndex,
    )
}

private fun applyActivityReorderUpsert(userId: String, item: SyncPushItem) {
    val payload = decodePayload<SyncActivityReorderUpsertPayload>(item.payload)
    val rawDayId = payload.dayId.ifBlank { item.id }
    val dayId = normalizeUuid(rawDayId)
    val tripId = DayRepository.findTripIdByDayId(dayId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (!TripRepository.isMember(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }

    val orderedIds = payload.orderedIds.map(::normalizeUuid)
    ActivityRepository.reorder(dayId, orderedIds)
}

private fun applyItineraryTrimUpsert(userId: String, item: SyncPushItem) {
    val payload = decodePayload<SyncItineraryTrimUpsertPayload>(item.payload)
    val tripId = normalizeUuid(payload.tripId)
    if (!TripRepository.isMember(tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    val dayIds = payload.dayIds.map(::normalizeUuid)

    when (payload.action.trim().lowercase()) {
        "keep" -> ItineraryDayRepository.markOutOfRange(dayIds, true)
        "remove" -> ItineraryDayRepository.deleteDays(dayIds)
        "extend_end" -> {
            val updated = TripRepository.extendTripEndByOutOfRangeDays(
                ownerId = userId,
                tripId = tripId,
                dayIds = dayIds,
            )
            if (updated == null) {
                throw SyncApplyException(REASON_FORBIDDEN)
            }
        }

        else -> throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
}

private fun applyNotificationSettingsUpsert(userId: String, item: SyncPushItem) {
    val payload = decodePayload<SyncNotificationSettingsUpsertPayload>(item.payload)
    val rows = payload.items.map { setting ->
        val key = setting.key.trim()
        if (key.isBlank()) {
            throw SyncApplyException(REASON_INVALID_PAYLOAD)
        }
        NotificationSettingRow(
            userId = userId,
            key = key,
            enabled = setting.enabled,
        )
    }
    NotificationRepository.upsertSettings(userId, rows)
}

private fun applyNotificationReadUpsert(userId: String, item: SyncPushItem) {
    val payload = decodePayload<SyncNotificationReadUpsertPayload>(item.payload)
    when (payload.mode.trim().lowercase()) {
        "single" -> {
            val rawId = payload.notificationId?.trim().orEmpty().ifBlank { item.id.trim() }
            val notificationId = normalizeUuid(rawId)
            val updated = NotificationRepository.markRead(userId, notificationId)
            if (!updated) {
                val exists = NotificationRepository.listForUser(userId).any { it.id == notificationId }
                if (!exists) {
                    throw SyncApplyException(REASON_NOT_FOUND)
                }
            }
        }

        READ_BULK_MODE_NON_COMMENT -> NotificationRepository.markReadBulkNonComment(userId)
        READ_BULK_MODE_IDEA_COMMENTS -> {
            val ideaId = payload.ideaId?.trim().orEmpty()
            if (ideaId.isBlank()) {
                throw SyncApplyException(REASON_INVALID_PAYLOAD)
            }
            NotificationRepository.markReadBulkIdeaComments(userId, normalizeUuid(ideaId))
        }

        else -> throw SyncApplyException(REASON_INVALID_PAYLOAD)
    }
}

private fun applyUserProfileUpsert(userId: String, item: SyncPushItem) {
    val payload = decodePayload<SyncUserProfileUpsertPayload>(item.payload)
    val existing = UserRepository.findById(userId) ?: throw SyncApplyException(REASON_NOT_FOUND)

    val normalizedName = payload.name.trim().takeIf { it.isNotBlank() }
        ?: throw SyncApplyException(REASON_INVALID_PAYLOAD)
    val normalizedPhotoUrl = when (val raw = payload.photoUrl) {
        null -> existing.photoUrl
        else -> raw.trim().takeIf { it.isNotBlank() }
    }

    val updated = UserRepository.updateUser(
        userId = userId,
        name = normalizedName,
        photoUrl = normalizedPhotoUrl,
    )
    if (updated == null) {
        throw SyncApplyException(REASON_NOT_FOUND)
    }
}

private fun applyAiSuggestionSaveUpsert(userId: String, item: SyncPushItem) {
    val payload = decodePayload<SyncAiSuggestionSaveUpsertPayload>(item.payload)
    val suggestionIdRaw = payload.suggestionId?.trim().orEmpty().ifBlank { item.id.trim() }
    val suggestionId = normalizeUuid(suggestionIdRaw)

    val suggestion = AiRepository.getSuggestionWithRequest(suggestionId)
        ?: throw SyncApplyException(REASON_DEPENDENCY_NOT_READY, retryable = true)
    if (!TripRepository.isMember(suggestion.tripId, userId)) {
        throw SyncApplyException(REASON_FORBIDDEN)
    }
    if (suggestion.suggestion.isSaved) {
        return
    }

    val idea = IdeaRepository.create(
        tripId = suggestion.tripId,
        authorId = userId,
        title = suggestion.suggestion.title,
        city = suggestion.suggestion.place?.trim()?.ifBlank { null },
        link = null,
        costAmount = suggestion.suggestion.estimatedCost,
        costType = null,
        notes = suggestion.suggestion.description ?: suggestion.requestDescription,
    )
    AiRepository.markSuggestionSaved(suggestionId, idea.id)
}

private fun normalizeEntity(raw: String): String {
    return when (raw.trim().lowercase()) {
        "day" -> "itinerary_day"
        "itinerary_day" -> "itinerary_day"
        else -> raw.trim().lowercase()
    }
}

private fun operationId(item: SyncPushItem): String {
    return item.changeId?.trim()?.takeIf { it.isNotBlank() } ?: item.id
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
