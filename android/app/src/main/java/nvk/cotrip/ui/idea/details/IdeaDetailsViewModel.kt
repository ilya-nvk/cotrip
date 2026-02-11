package nvk.cotrip.ui.idea.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.BuildConfig
import nvk.cotrip.R
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.ws.CommentCreatedPayload
import nvk.cotrip.data.network.ws.CommentWsEvent
import nvk.cotrip.data.network.ws.CommentsWebSocket
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.notifications.SystemNotificationManager
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.idea.common.IdeaDayOptionUi
import nvk.cotrip.ui.idea.common.IdeaDayPickerState
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import nvk.cotrip.util.AppLogger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class IdeaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val ideaRepository: IdeaRepository,
    private val itineraryRepository: ItineraryRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val systemNotificationManager: SystemNotificationManager,
    private val sessionStore: SessionStore,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val json: Json,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.IdeaDetails.ARG_TRIP_ID])
    private val ideaId: String =
        checkNotNull(savedStateHandle[Destination.IdeaDetails.ARG_IDEA_ID])

    private val commentsSocket = CommentsWebSocket(okHttpClient, json)
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0
    private val pendingComments = linkedMapOf<String, PendingComment>()
    private val pendingTimeoutJobs = mutableMapOf<String, Job>()
    private var dayOptions: List<IdeaDayOptionUi> = emptyList()
    private var membersById: Map<String, MemberDto> = emptyMap()
    private var meId: String? = null
    private var currencySymbol: String = "€"

    private val _state = MutableStateFlow(
        IdeaDetailsState(
            tripId = tripId,
            ideaId = ideaId,
            title = "",
            city = "",
            link = "",
            cost = "",
            notes = "",
            status = "pending",
            addedDay = null,
            isOwner = false,
            isUpdatingStatus = false,
            selectedTab = IdeaDetailsTab.Details,
            commentsCount = 0,
            discussion = emptyList(),
            commentInput = "",
            dayPicker = null
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<IdeaDetailsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeSocket()
        connectSocket()
        observeDetails()
        refreshDetails(showErrorToast = false)
    }

    override fun onCleared() {
        reconnectJob?.cancel()
        pendingTimeoutJobs.values.forEach { it.cancel() }
        pendingTimeoutJobs.clear()
        commentsSocket.disconnect()
        super.onCleared()
    }

    fun onEvent(event: IdeaDetailsEvent) {
        when (event) {
            IdeaDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            IdeaDetailsEvent.OnRefresh -> refreshDetails(showErrorToast = true)
            IdeaDetailsEvent.OnEditClick -> appNavigator.navigate(
                Destination.EditIdea(tripId, ideaId)
            )

            IdeaDetailsEvent.OnAddToItineraryClick -> openDayPicker()
            IdeaDetailsEvent.OnDeleteClick -> deleteIdea()
            IdeaDetailsEvent.OnApproveClick -> updateStatus(approved = true)
            IdeaDetailsEvent.OnRejectClick -> updateStatus(approved = false)
            IdeaDetailsEvent.OnDismissDayPicker -> dismissDayPicker()
            is IdeaDetailsEvent.OnDaySelected -> selectDay(event.day)
            is IdeaDetailsEvent.OnTabSelected -> {
                _state.update { it.copy(selectedTab = event.tab) }
                if (event.tab == IdeaDetailsTab.Discussion) {
                    markDiscussionNotificationsRead()
                }
            }
            is IdeaDetailsEvent.OnCommentChange -> _state.update { it.copy(commentInput = event.value) }
            IdeaDetailsEvent.OnSendComment -> sendComment()
            is IdeaDetailsEvent.OnRetryComment -> retryComment(event.localId)
            is IdeaDetailsEvent.OnDeletePendingComment -> deletePendingComment(event.localId)
        }
    }

    private fun observeDetails() {
        viewModelScope.launch {
            val ideaFlow = ideaRepository.getIdea(ideaId)
            val tripFlow = tripRepository.getTrip(tripId)
            val itineraryFlow = itineraryRepository.observeItinerary(tripId)
            val membersFlow = tripRepository.tripMembers(tripId)
            val commentsFlow = ideaRepository.observeComments(ideaId)
            combine(
                ideaFlow,
                tripFlow,
                itineraryFlow,
                membersFlow,
                commentsFlow,
            ) { idea, trip, itinerary, members, comments ->
                val memberMap = members.associateBy { it.userId }
                val addedDay = itinerary
                    .sortedBy { it.dayNumber }
                    .firstOrNull { day ->
                        day.activities.any { activity -> activity.sourceIdeaId == idea.id }
                    }
                    ?.dayNumber
                ObservedPayloadBase(
                    idea = idea,
                    trip = trip,
                    comments = comments,
                    itinerary = itinerary,
                    membersById = memberMap,
                    addedDay = addedDay,
                )
            }.combine(userRepository.me) { base, me ->
                val myId = me?.id
                val isOwner = myId != null && base.trip.ownerId == myId
                ObservedPayload(
                    idea = base.idea,
                    trip = base.trip,
                    comments = base.comments,
                    itinerary = base.itinerary,
                    membersById = base.membersById,
                    meId = myId,
                    isOwner = isOwner,
                    addedDay = base.addedDay,
                )
            }.collect { payload ->
                currencySymbol = currencySymbolFor(payload.trip.currencyCode)
                membersById = payload.membersById
                meId = payload.meId
                dayOptions = payload.itinerary.filter { !it.isOutOfRange }.map { it.toDayOption() }
                val serverDiscussion = payload.comments
                    .sortedBy { parseTimestamp(it.createdAt)?.toEpochMilli() ?: 0L }
                    .map { it.toDiscussion(meId, membersById) }
                val discussion = mergeDiscussionWithPending(
                    serverDiscussion = serverDiscussion,
                    pending = pendingComments.values.toList(),
                    meId = meId,
                    membersById = membersById
                )
                _state.update { current ->
                    current.copy(
                        title = payload.idea.title,
                        city = payload.idea.city.orEmpty(),
                        link = payload.idea.link.orEmpty(),
                        cost = payload.idea.costAmount?.let { formatCost(it, currencySymbol) }
                            .orEmpty(),
                        notes = payload.idea.notes.orEmpty(),
                        status = payload.idea.status,
                        addedDay = payload.addedDay,
                        isOwner = payload.isOwner,
                        commentsCount = serverDiscussion.count { it is IdeaDiscussionItemUi.Message },
                        discussion = discussion
                    )
                }
            }
        }
    }

    private fun refreshDetails(showErrorToast: Boolean) {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.refreshTrips().getOrThrow()
                    tripRepository.tripMembers(tripId).first()
                    itineraryRepository.refreshItinerary(tripId).getOrThrow()
                    ideaRepository.refreshIdeas(tripId).getOrThrow()
                    ideaRepository.refreshComments(ideaId).getOrThrow()
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> if (showErrorToast) {
                    emit(IdeaDetailsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun connectSocket() {
        val token = sessionStore.getAccessToken().orEmpty()
        if (token.isBlank()) {
            scheduleReconnect()
            return
        }
        reconnectJob?.cancel()
        commentsSocket.connect(BuildConfig.API_BASE_URL, tripId, token)
        reconnectAttempt = 0
        refreshCommentsSilently()
    }

    private fun observeSocket() {
        viewModelScope.launch {
            commentsSocket.events.collect { event ->
                when (event) {
                    is CommentWsEvent.CommentCreated -> handleCommentCreated(event.payload)
                    is CommentWsEvent.CommentDeleted -> handleCommentDeleted(event.payload.id)
                    is CommentWsEvent.Closed -> {
                        AppLogger.w(
                            TAG,
                            "comments socket closed code=${event.code} reason=${event.reason}, scheduling reconnect"
                        )
                        scheduleReconnect()
                    }
                    is CommentWsEvent.Error -> {
                        AppLogger.w(TAG, "comments socket error, scheduling reconnect", event.cause)
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        val delayMs = (SOCKET_RECONNECT_BASE_DELAY_MS * (1L shl reconnectAttempt.coerceAtMost(5)))
            .coerceAtMost(SOCKET_RECONNECT_MAX_DELAY_MS)
        reconnectAttempt += 1
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            connectSocket()
        }
    }

    private fun refreshCommentsSilently() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ideaRepository.refreshComments(ideaId).getOrThrow()
                }
            }.onFailure { error ->
                AppLogger.w(TAG, "refreshCommentsSilently failed for ideaId=$ideaId", error)
            }
        }
    }

    private fun handleCommentCreated(payload: CommentCreatedPayload) {
        if (payload.ideaId != ideaId) return
        val resolvedLocalId = resolvePendingOnCreated(payload)
        _state.update { current ->
            val exists = current.discussion.any { item -> item.id == payload.id }
            if (exists) return@update current
            val message = payload.toDiscussionItem(meId, membersById)
            val increment = if (message is IdeaDiscussionItemUi.Message) 1 else 0
            val withoutLocal = if (resolvedLocalId != null) {
                current.discussion.filterNot { item ->
                    (item as? IdeaDiscussionItemUi.Message)?.localId == resolvedLocalId
                }
            } else {
                current.discussion
            }
            current.copy(
                discussion = withoutLocal + message,
                commentsCount = current.commentsCount + increment
            )
        }
        if (_state.value.selectedTab == IdeaDetailsTab.Discussion) {
            markDiscussionNotificationsRead()
        }
        refreshCommentsSilently()
    }

    private fun handleCommentDeleted(commentId: String) {
        _state.update { current ->
            val deleted = current.discussion.firstOrNull { it.id == commentId }
            val updated = current.discussion.filterNot { it.id == commentId }
            if (updated.size == current.discussion.size) return@update current
            val decrement = if (deleted is IdeaDiscussionItemUi.Message) 1 else 0
            current.copy(
                discussion = updated,
                commentsCount = (current.commentsCount - decrement).coerceAtLeast(0)
            )
        }
        refreshCommentsSilently()
    }

    private fun openDayPicker() {
        if (_state.value.addedDay != null) return
        if (dayOptions.isEmpty()) {
            emit(IdeaDetailsEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        _state.update {
            it.copy(
                dayPicker = IdeaDayPickerState(
                    ideaId = ideaId,
                    days = dayOptions
                )
            )
        }
    }

    private fun dismissDayPicker() {
        _state.update { it.copy(dayPicker = null) }
    }

    private fun selectDay(day: IdeaDayOptionUi) {
        _state.update { it.copy(dayPicker = null) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    ideaRepository.convertIdeaToActivity(ideaId, ConvertIdeaRequest(dayId = day.id))
                }
            }) {
                is ApiResult.Success -> {
                    withContext(Dispatchers.IO) {
                        itineraryRepository.refreshItinerary(tripId).getOrThrow()
                    }
                    _state.update { it.copy(addedDay = day.dayNumber) }
                    emit(IdeaDetailsEffect.ShowToastRes(R.string.idea_details_added_toast))
                }

                is ApiResult.Failure -> {
                    emit(IdeaDetailsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun sendComment() {
        val input = _state.value.commentInput.trim()
        if (input.isBlank()) return
        val token = sessionStore.getAccessToken()
        if (token.isNullOrBlank()) {
            emit(IdeaDetailsEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        val localId = UUID.randomUUID().toString()
        addPendingComment(localId = localId, text = input)
        _state.update { it.copy(commentInput = "") }
        sendPendingComment(localId)
    }

    private fun retryComment(localId: String) {
        val pending = pendingComments[localId] ?: return
        if (pending.status == PendingStatus.Sending) return
        pendingComments[localId] = pending.copy(status = PendingStatus.Sending)
        updatePendingMessageStatus(localId, IdeaDiscussionItemUi.DeliveryState.Sending)
        sendPendingComment(localId)
    }

    private fun deletePendingComment(localId: String) {
        pendingComments.remove(localId)
        pendingTimeoutJobs.remove(localId)?.cancel()
        _state.update { current ->
            current.copy(
                discussion = current.discussion.filterNot { item ->
                    (item as? IdeaDiscussionItemUi.Message)?.localId == localId
                }
            )
        }
    }

    private fun addPendingComment(localId: String, text: String) {
        val nowMillis = System.currentTimeMillis()
        pendingComments[localId] = PendingComment(
            localId = localId,
            text = text,
            createdAtMillis = nowMillis,
            status = PendingStatus.Sending
        )
        _state.update { current ->
            val pendingMessage = pendingToMessage(
                pending = pendingComments.getValue(localId),
                meId = meId,
                membersById = membersById
            )
            current.copy(discussion = current.discussion + pendingMessage)
        }
    }

    private fun sendPendingComment(localId: String) {
        val pending = pendingComments[localId] ?: return
        val sent = commentsSocket.sendCreate(
            ideaId = ideaId,
            body = pending.text,
            clientMessageId = localId
        )
        if (!sent) {
            markPendingFailed(localId)
            scheduleReconnect()
            return
        }
        startPendingTimeout(localId)
    }

    private fun startPendingTimeout(localId: String) {
        pendingTimeoutJobs.remove(localId)?.cancel()
        pendingTimeoutJobs[localId] = viewModelScope.launch {
            delay(COMMENT_SEND_TIMEOUT_MS)
            val pending = pendingComments[localId] ?: return@launch
            if (pending.status == PendingStatus.Sending) {
                markPendingFailed(localId)
            }
        }
    }

    private fun markPendingFailed(localId: String) {
        val pending = pendingComments[localId] ?: return
        pendingComments[localId] = pending.copy(status = PendingStatus.Failed)
        updatePendingMessageStatus(localId, IdeaDiscussionItemUi.DeliveryState.Failed)
    }

    private fun updatePendingMessageStatus(
        localId: String,
        deliveryState: IdeaDiscussionItemUi.DeliveryState,
    ) {
        _state.update { current ->
            current.copy(
                discussion = current.discussion.map { item ->
                    val message = item as? IdeaDiscussionItemUi.Message ?: return@map item
                    if (message.localId == localId) {
                        message.copy(deliveryState = deliveryState)
                    } else {
                        message
                    }
                }
            )
        }
    }

    private fun resolvePendingOnCreated(payload: CommentCreatedPayload): String? {
        val directLocalId = payload.clientMessageId?.takeIf { pendingComments.containsKey(it) }
        val fallbackLocalId = if (directLocalId == null &&
            payload.authorId == meId &&
            !payload.type.equals("system", ignoreCase = true)
        ) {
            pendingComments.values
                .filter { it.text == payload.body && it.status == PendingStatus.Sending }
                .minByOrNull { it.createdAtMillis }
                ?.localId
        } else {
            null
        }
        val localId = directLocalId ?: fallbackLocalId ?: return null
        pendingComments.remove(localId)
        pendingTimeoutJobs.remove(localId)?.cancel()
        return localId
    }

    private fun updateStatus(approved: Boolean) {
        if (_state.value.isUpdatingStatus) return
        _state.update { it.copy(isUpdatingStatus = true) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    if (approved) {
                        ideaRepository.approveIdea(ideaId)
                    } else {
                        ideaRepository.rejectIdea(ideaId)
                    }
                }
            }) {
                is ApiResult.Success -> {
                    val idea = result.data
                    _state.update { it.copy(status = idea.status, isUpdatingStatus = false) }
                    val toast = if (approved) {
                        R.string.idea_details_approved_toast
                    } else {
                        R.string.idea_details_rejected_toast
                    }
                    emit(IdeaDetailsEffect.ShowToastRes(toast))
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isUpdatingStatus = false) }
                    emit(IdeaDetailsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun deleteIdea() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) { ideaRepository.deleteIdea(ideaId) }
            }) {
                is ApiResult.Success -> {
                    emit(IdeaDetailsEffect.ShowToastRes(R.string.idea_details_deleted_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(IdeaDetailsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun emit(effect: IdeaDetailsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private fun markDiscussionNotificationsRead() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    systemNotificationManager.onIdeaDiscussionRead(ideaId)
                    notificationRepository.refreshNotifications().getOrThrow()
                    val items = notificationRepository.notifications.first()
                    val toMark = items.filter { item ->
                        item.readAt == null &&
                            item.type == "idea_comment" &&
                            runCatching {
                                item.payload.jsonObject["ideaId"]?.jsonPrimitive?.contentOrNull == ideaId
                            }.getOrDefault(false)
                    }
                    for (item in toMark) {
                        runCatching { notificationRepository.markRead(item.id) }
                        systemNotificationManager.onMarkedRead(item.id)
                    }
                }
            }
        }
    }

    private data class ObservedPayload(
        val idea: IdeaDto,
        val trip: nvk.cotrip.data.network.dto.TripDto,
        val comments: List<CommentDto>,
        val itinerary: List<ItineraryDayDto>,
        val membersById: Map<String, MemberDto>,
        val meId: String?,
        val isOwner: Boolean,
        val addedDay: Int?,
    )

    private data class ObservedPayloadBase(
        val idea: IdeaDto,
        val trip: nvk.cotrip.data.network.dto.TripDto,
        val comments: List<CommentDto>,
        val itinerary: List<ItineraryDayDto>,
        val membersById: Map<String, MemberDto>,
        val addedDay: Int?,
    )

}

private fun CommentDto.toDiscussion(
    meId: String?,
    membersById: Map<String, MemberDto>,
): IdeaDiscussionItemUi {
    if (type.equals("system", ignoreCase = true)) {
        return IdeaDiscussionItemUi.System(
            id = id,
            text = body,
            time = formatTimestamp(createdAt),
        )
    }
    val member = membersById[authorId]
    val name = member?.name ?: "Unknown"
    val initials = member?.initials ?: initialsFromName(name)
    return IdeaDiscussionItemUi.Message(
        id = id,
        author = name,
        initials = initials,
        photoUrl = member?.photoUrl,
        text = body,
        time = formatTimestamp(createdAt),
        isMe = authorId == meId
    )
}

private fun CommentCreatedPayload.toDiscussion(
    meId: String?,
    membersById: Map<String, MemberDto>,
): IdeaDiscussionItemUi.Message {
    val member = membersById[authorId]
    val name = member?.name ?: "Unknown"
    val initials = member?.initials ?: initialsFromName(name)
    return IdeaDiscussionItemUi.Message(
        id = id,
        author = name,
        initials = initials,
        photoUrl = member?.photoUrl,
        text = body,
        time = formatTimestamp(createdAt),
        isMe = authorId == meId,
        deliveryState = IdeaDiscussionItemUi.DeliveryState.Sent
    )
}

private fun CommentCreatedPayload.toDiscussionItem(
    meId: String?,
    membersById: Map<String, MemberDto>,
): IdeaDiscussionItemUi {
    return if (type.equals("system", ignoreCase = true)) {
        IdeaDiscussionItemUi.System(
            id = id,
            text = body,
            time = formatTimestamp(createdAt),
        )
    } else {
        toDiscussion(meId, membersById)
    }
}

private fun ItineraryDayDto.toDayOption(): IdeaDayOptionUi {
    val date = LocalDate.parse(date)
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    return IdeaDayOptionUi(
        id = id,
        dayNumber = dayNumber,
        dateText = date.format(formatter),
        city = city.orEmpty(),
    )
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}

private fun formatCost(amount: Double, currencySymbol: String): String {
    val display = if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
    return "$currencySymbol$display"
}

private fun parseTimestamp(raw: String): Instant? {
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant()
        }.getOrNull()
}

private fun pendingToMessage(
    pending: PendingComment,
    meId: String?,
    membersById: Map<String, MemberDto>,
): IdeaDiscussionItemUi.Message {
    val me = meId?.let { membersById[it] }
    val name = me?.name ?: "You"
    val initials = me?.initials ?: initialsFromName(name)
    val deliveryState = when (pending.status) {
        PendingStatus.Sending -> IdeaDiscussionItemUi.DeliveryState.Sending
        PendingStatus.Failed -> IdeaDiscussionItemUi.DeliveryState.Failed
    }
    return IdeaDiscussionItemUi.Message(
        id = localMessageId(pending.localId),
        author = name,
        initials = initials,
        photoUrl = me?.photoUrl,
        text = pending.text,
        time = formatTimestamp(Instant.ofEpochMilli(pending.createdAtMillis).toString()),
        isMe = true,
        deliveryState = deliveryState,
        localId = pending.localId
    )
}

private fun mergeDiscussionWithPending(
    serverDiscussion: List<IdeaDiscussionItemUi>,
    pending: List<PendingComment>,
    meId: String?,
    membersById: Map<String, MemberDto>,
): List<IdeaDiscussionItemUi> {
    if (pending.isEmpty()) return serverDiscussion
    val merged = serverDiscussion.toMutableList()
    val existingIds = merged.mapTo(mutableSetOf()) { it.id }
    pending.sortedBy { it.createdAtMillis }.forEach { item ->
        val localId = localMessageId(item.localId)
        if (localId in existingIds) return@forEach
        merged += pendingToMessage(item, meId, membersById)
    }
    return merged
}

private fun localMessageId(localId: String): String = "local:$localId"

private data class PendingComment(
    val localId: String,
    val text: String,
    val createdAtMillis: Long,
    val status: PendingStatus,
)

private enum class PendingStatus {
    Sending,
    Failed,
}

private const val TAG = "IdeaDetailsVM"
private const val SOCKET_RECONNECT_BASE_DELAY_MS = 1_000L
private const val SOCKET_RECONNECT_MAX_DELAY_MS = 30_000L
private const val COMMENT_SEND_TIMEOUT_MS = 8_000L

private fun formatTimestamp(raw: String): String {
    val instant = parseTimestamp(raw) ?: return raw
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

private fun initialsFromName(name: String): String {
    return name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
}
