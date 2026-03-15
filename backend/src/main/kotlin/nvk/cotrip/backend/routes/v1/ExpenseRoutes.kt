package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.db.ExpenseParticipantRow
import nvk.cotrip.backend.db.ExpenseRepository
import nvk.cotrip.backend.db.TripMemberRepository
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.UserRepository
import nvk.cotrip.backend.notifications.NotificationService
import java.time.LocalDate

@Serializable
data class ExpenseParticipantInput(
    val userId: String,
    val shareAmount: Double? = null,
    val isIncluded: Boolean = true,
    val isPaid: Boolean = false,
)

@Serializable
data class ExpenseCreateRequest(
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
data class ExpenseUpdateRequest(
    val title: String? = null,
    val amount: Double? = null,
    val status: String? = null,
    val paidById: String? = null,
    val date: String? = null,
    val splitType: String? = null,
    val note: String? = null,
    val participants: List<ExpenseParticipantInput>? = null,
)

private fun isValidExpenseStatus(status: String): Boolean = status == "planned" || status == "paid"

private fun isValidSplitType(splitType: String): Boolean = splitType == "equally" || splitType == "custom"

fun Route.expenseRoutes() {
    authenticate("auth-jwt") {
        get("/v1/trips/{tripId}/expenses") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val expenses = ExpenseRepository.listByTrip(tripId)
            val participantMap = ExpenseRepository.listParticipants(expenses.map { it.id })
            val items = expenses.map { expense ->
                val participants = participantMap[expense.id] ?: emptyList()
                expense.toDto(participants)
            }

            call.respond(mapOf("items" to items, "nextCursor" to null))
        }

        post("/v1/trips/{tripId}/expenses") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val trip = TripRepository.getTripById(tripId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val request = call.receive<ExpenseCreateRequest>()
            if (!isValidExpenseStatus(request.status) || !isValidSplitType(request.splitType)) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (request.participants.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val currencyCode = request.currencyCode ?: trip.currencyCode
            if (currencyCode != trip.currencyCode) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val memberIds = TripMemberRepository.listMemberIds(tripId)
            if (request.paidById != null && request.paidById !in memberIds) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val participants = request.participants.map { participant ->
                if (participant.userId !in memberIds) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                ExpenseParticipantRow(
                    expenseId = "",
                    userId = participant.userId,
                    shareAmount = participant.shareAmount,
                    isIncluded = participant.isIncluded,
                    isPaid = participant.isPaid,
                )
            }

            val expense = ExpenseRepository.create(
                tripId = tripId,
                title = request.title,
                amount = request.amount,
                currencyCode = currencyCode,
                status = request.status,
                paidById = request.paidById,
                expenseDate = request.date?.let { LocalDate.parse(it) },
                splitType = request.splitType,
                note = request.note,
                participants = participants,
            )

            val actorName = UserRepository.findById(userId)?.name ?: "Someone"
            NotificationService.notifyExpenseCreated(
                tripId = tripId,
                expenseId = expense.id,
                actorUserId = userId,
                actorName = actorName,
                title = expense.title,
                amount = expense.amount,
                currencyCode = expense.currencyCode
            )

            call.respond(expense.toDto(participants))
        }

        get("/v1/expenses/{expenseId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val expenseId = call.parameters["expenseId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val expense = ExpenseRepository.get(expenseId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            if (!TripRepository.isMember(expense.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val participants = ExpenseRepository.listParticipants(listOf(expense.id))[expense.id] ?: emptyList()
            call.respond(expense.toDto(participants))
        }

        patch("/v1/expenses/{expenseId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val expenseId = call.parameters["expenseId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val existing = ExpenseRepository.get(expenseId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            if (!TripRepository.isMember(existing.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@patch
            }

            val request = call.receive<ExpenseUpdateRequest>()
            if (request.status != null && !isValidExpenseStatus(request.status)) {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }
            if (request.splitType != null && !isValidSplitType(request.splitType)) {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val memberIds = TripMemberRepository.listMemberIds(existing.tripId)
            if (request.paidById != null && request.paidById !in memberIds) {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val participantRows = request.participants?.let { participants ->
                if (participants.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@patch
                }
                participants.map { participant ->
                    if (participant.userId !in memberIds) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@patch
                    }
                    ExpenseParticipantRow(
                        expenseId = expenseId,
                        userId = participant.userId,
                        shareAmount = participant.shareAmount,
                        isIncluded = participant.isIncluded,
                        isPaid = participant.isPaid,
                    )
                }
            }

            val updated = ExpenseRepository.update(
                expenseId = expenseId,
                title = request.title,
                amount = request.amount,
                status = request.status,
                paidById = request.paidById,
                expenseDate = request.date?.let { LocalDate.parse(it) },
                splitType = request.splitType,
                note = request.note,
                participants = participantRows,
            ) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            val statusBecamePaid = existing.status != "paid" && updated.status == "paid"
            if (statusBecamePaid) {
                val actorName = UserRepository.findById(userId)?.name ?: "Someone"
                NotificationService.notifyExpenseSettlement(
                    tripId = updated.tripId,
                    expenseId = updated.id,
                    actorUserId = userId,
                    actorName = actorName,
                    title = updated.title
                )
            }

            val participants = participantRows ?: ExpenseRepository.listParticipants(listOf(expenseId))[expenseId] ?: emptyList()
            call.respond(updated.toDto(participants))
        }

        delete("/v1/expenses/{expenseId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val expenseId = call.parameters["expenseId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            val expense = ExpenseRepository.get(expenseId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            if (!TripRepository.isMember(expense.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            val deleted = ExpenseRepository.softDelete(expenseId)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun nvk.cotrip.backend.db.ExpenseRow.toDto(participants: List<nvk.cotrip.backend.db.ExpenseParticipantRow>): ExpenseDto {
    return ExpenseDto(
        id = id,
        tripId = tripId,
        title = title,
        amount = amount,
        currencyCode = currencyCode,
        status = status,
        paidById = paidById,
        date = expenseDate?.toString(),
        splitType = splitType,
        note = note,
        participants = participants.map { participant ->
            ExpenseParticipantDto(
                userId = participant.userId,
                shareAmount = participant.shareAmount,
                isIncluded = participant.isIncluded,
                isPaid = participant.isPaid,
                name = participant.userName,
            )
        },
    )
}
