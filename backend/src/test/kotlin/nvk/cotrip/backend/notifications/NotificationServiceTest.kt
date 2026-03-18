package nvk.cotrip.backend.notifications

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import nvk.cotrip.backend.db.NotificationRepository
import nvk.cotrip.backend.db.NotificationRow
import nvk.cotrip.backend.db.PushTokenRepository
import nvk.cotrip.backend.db.PushTokenRow
import nvk.cotrip.backend.db.TripMemberRepository
import java.time.OffsetDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class NotificationServiceTest {

    @AfterTest
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun given_eligibleMembersAndInvalidToken_when_notifyIdeaComment_then_createsNotificationAndRemovesBadToken() = runBlocking {
        // GIVEN
        mockkObject(TripMemberRepository, NotificationRepository, PushTokenRepository, FirebasePushService)
        val payloadSlot = slot<String>()
        every { TripMemberRepository.listMemberIds("trip-1") } returns setOf("actor", "user-2", "user-3")
        every { NotificationRepository.isSettingEnabled("user-2", "discussions_comments") } returns true
        every { NotificationRepository.isSettingEnabled("user-3", "discussions_comments") } returns false
        every {
            NotificationRepository.create(
                userId = "user-2",
                type = "idea_comment",
                payload = capture(payloadSlot),
            )
        } returns notificationRow(userId = "user-2", type = "idea_comment", payload = """{"ok":true}""")
        every { PushTokenRepository.listByUserId("user-2") } returns listOf(
            pushTokenRow(token = "bad-token", userId = "user-2"),
            pushTokenRow(token = "good-token", userId = "user-2"),
        )
        every { FirebasePushService.sendDataMessage("bad-token", any()) } returns PushDeliveryStatus.INVALID_TOKEN
        every { FirebasePushService.sendDataMessage("good-token", any()) } returns PushDeliveryStatus.SENT
        every { PushTokenRepository.removeByToken("bad-token") } returns true

        // WHEN
        NotificationService.notifyIdeaComment(
            tripId = "trip-1",
            ideaId = "idea-1",
            actorUserId = "actor",
            actorName = "Alice",
            body = "Comment body",
        )

        // THEN
        verify(exactly = 1) { NotificationRepository.create("user-2", "idea_comment", any()) }
        verify(exactly = 0) { NotificationRepository.create("user-3", any(), any()) }
        verify(exactly = 1) { PushTokenRepository.removeByToken("bad-token") }
        assertTrue(payloadSlot.captured.contains(""""ideaId":"idea-1""""))
        assertTrue(payloadSlot.captured.contains(""""body":"Comment body""""))
    }

    @Test
    fun given_onlyActorInTrip_when_notifyExpenseCreated_then_noNotificationsCreated() = runBlocking {
        // GIVEN
        mockkObject(TripMemberRepository, NotificationRepository, PushTokenRepository, FirebasePushService)
        every { TripMemberRepository.listMemberIds("trip-1") } returns setOf("actor")

        // WHEN
        NotificationService.notifyExpenseCreated(
            tripId = "trip-1",
            expenseId = "expense-1",
            actorUserId = "actor",
            actorName = "Alice",
            title = "Dinner",
            amount = 100.0,
            currencyCode = "EUR",
        )

        // THEN
        verify(exactly = 0) { NotificationRepository.create(any(), any(), any()) }
        verify(exactly = 0) { PushTokenRepository.listByUserId(any()) }
    }

    @Test
    fun given_onlyActorInTrip_when_notifyIdeaCreated_then_noNotificationsCreated() = runBlocking {
        // GIVEN
        mockkObject(TripMemberRepository, NotificationRepository, PushTokenRepository, FirebasePushService)
        every { TripMemberRepository.listMemberIds("trip-1") } returns setOf("actor")

        // WHEN
        NotificationService.notifyIdeaCreated(
            tripId = "trip-1",
            ideaId = "idea-1",
            actorUserId = "actor",
            actorName = "Alice",
            ideaTitle = "Louvre visit",
        )

        // THEN
        verify(exactly = 0) { NotificationRepository.create(any(), any(), any()) }
    }

    @Test
    fun given_eligibleMember_when_notifyIdeaCreated_then_createsNotificationWithIdeaTitle() = runBlocking {
        // GIVEN
        mockkObject(TripMemberRepository, NotificationRepository, PushTokenRepository, FirebasePushService)
        val payloadSlot = slot<String>()
        every { TripMemberRepository.listMemberIds("trip-1") } returns setOf("actor", "user-2")
        every { NotificationRepository.isSettingEnabled("user-2", "discussions_comments") } returns true
        every {
            NotificationRepository.create(userId = "user-2", type = "idea_created", payload = capture(payloadSlot))
        } returns notificationRow(userId = "user-2", type = "idea_created", payload = """{}""")
        every { PushTokenRepository.listByUserId("user-2") } returns emptyList()

        // WHEN
        NotificationService.notifyIdeaCreated(
            tripId = "trip-1",
            ideaId = "idea-1",
            actorUserId = "actor",
            actorName = "Alice",
            ideaTitle = "Louvre visit",
        )

        // THEN
        verify(exactly = 1) { NotificationRepository.create("user-2", "idea_created", any()) }
        assertTrue(payloadSlot.captured.contains(""""ideaId":"idea-1""""))
        assertTrue(payloadSlot.captured.contains(""""ideaTitle":"Louvre visit""""))
    }

    @Test
    fun given_eligibleMemberWithSettingDisabled_when_notifyExpenseCreated_then_noNotificationCreated() = runBlocking {
        // GIVEN
        mockkObject(TripMemberRepository, NotificationRepository, PushTokenRepository, FirebasePushService)
        every { TripMemberRepository.listMemberIds("trip-1") } returns setOf("actor", "user-2")
        every { NotificationRepository.isSettingEnabled("user-2", "expenses_new") } returns false

        // WHEN
        NotificationService.notifyExpenseCreated(
            tripId = "trip-1",
            expenseId = "expense-1",
            actorUserId = "actor",
            actorName = "Alice",
            title = "Dinner",
            amount = 100.0,
            currencyCode = "EUR",
        )

        // THEN
        verify(exactly = 0) { NotificationRepository.create(any(), any(), any()) }
    }

    @Test
    fun given_eligibleMember_when_notifyExpenseCreated_then_createsNotificationWithPayload() = runBlocking {
        // GIVEN
        mockkObject(TripMemberRepository, NotificationRepository, PushTokenRepository, FirebasePushService)
        val payloadSlot = slot<String>()
        every { TripMemberRepository.listMemberIds("trip-1") } returns setOf("actor", "user-2")
        every { NotificationRepository.isSettingEnabled("user-2", "expenses_new") } returns true
        every {
            NotificationRepository.create(userId = "user-2", type = "expense_created", payload = capture(payloadSlot))
        } returns notificationRow(userId = "user-2", type = "expense_created", payload = """{}""")
        every { PushTokenRepository.listByUserId("user-2") } returns emptyList()

        // WHEN
        NotificationService.notifyExpenseCreated(
            tripId = "trip-1",
            expenseId = "expense-1",
            actorUserId = "actor",
            actorName = "Alice",
            title = "Dinner",
            amount = 100.0,
            currencyCode = "EUR",
        )

        // THEN
        verify(exactly = 1) { NotificationRepository.create("user-2", "expense_created", any()) }
        assertTrue(payloadSlot.captured.contains(""""expenseId":"expense-1""""))
        assertTrue(payloadSlot.captured.contains(""""title":"Dinner""""))
        assertTrue(payloadSlot.captured.contains(""""amount":100.0"""))
    }

    @Test
    fun given_pushFails_when_notifyExpenseSettlement_then_doesNotRemoveToken() = runBlocking {
        // GIVEN
        mockkObject(TripMemberRepository, NotificationRepository, PushTokenRepository, FirebasePushService)
        every { TripMemberRepository.listMemberIds("trip-1") } returns setOf("actor", "user-2")
        every { NotificationRepository.isSettingEnabled("user-2", "expenses_settlements") } returns true
        every {
            NotificationRepository.create(
                userId = "user-2",
                type = "expense_settlement",
                payload = any(),
            )
        } returns notificationRow(userId = "user-2", type = "expense_settlement", payload = """{"ok":true}""")
        every { PushTokenRepository.listByUserId("user-2") } returns listOf(
            pushTokenRow(token = "failed-token", userId = "user-2")
        )
        every { FirebasePushService.sendDataMessage("failed-token", any()) } returns PushDeliveryStatus.FAILED

        // WHEN
        NotificationService.notifyExpenseSettlement(
            tripId = "trip-1",
            expenseId = "expense-1",
            actorUserId = "actor",
            actorName = "Alice",
            title = "Hotel",
        )

        // THEN
        verify(exactly = 1) { NotificationRepository.create("user-2", "expense_settlement", any()) }
        verify(exactly = 0) { PushTokenRepository.removeByToken(any()) }
    }

    private fun notificationRow(
        userId: String,
        type: String,
        payload: String,
    ): NotificationRow = NotificationRow(
        id = "notification-1",
        userId = userId,
        type = type,
        payload = payload,
        createdAt = OffsetDateTime.now(),
        readAt = null,
    )

    private fun pushTokenRow(
        token: String,
        userId: String,
    ): PushTokenRow = PushTokenRow(
        token = token,
        userId = userId,
        platform = "android",
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
}
