package nvk.cotrip.ui.idea.details

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.notifications.AppRuntimeState
import nvk.cotrip.notifications.NotificationNavigationState
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripDivider
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.idea.common.IdeaDayPickerSheet
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.Error
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.PrimaryLight
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.WhiteCards

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaDetailsScreen(
    viewModel: IdeaDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(IdeaDetailsEvent.OnRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is IdeaDetailsEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    LaunchedEffect(state.ideaId) {
        if (NotificationNavigationState.consumeOpenDiscussion(state.ideaId)) {
            viewModel.onEvent(IdeaDetailsEvent.OnTabSelected(IdeaDetailsTab.Discussion))
        }
    }

    val dayPicker = state.dayPicker
    DisposableEffect(state.selectedTab, state.ideaId) {
        if (state.selectedTab == IdeaDetailsTab.Discussion) {
            AppRuntimeState.setActiveDiscussionIdeaId(state.ideaId)
        } else {
            AppRuntimeState.clearActiveDiscussionIdeaId(state.ideaId)
        }
        onDispose {
            AppRuntimeState.clearActiveDiscussionIdeaId(state.ideaId)
        }
    }

    if (dayPicker != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(IdeaDetailsEvent.OnDismissDayPicker) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            IdeaDayPickerSheet(
                days = dayPicker.days,
                onSelect = { viewModel.onEvent(IdeaDetailsEvent.OnDaySelected(it)) }
            )
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.ArrowBack,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(IdeaDetailsEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.idea_details_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                actions = {
                    CoTripIconButton(
                        icon = CoTripIcons.Edit,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(IdeaDetailsEvent.OnEditClick) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            when (state.selectedTab) {
                IdeaDetailsTab.Details -> DetailsBottomBar(
                    addedDay = state.addedDay,
                    onAddClick = { viewModel.onEvent(IdeaDetailsEvent.OnAddToItineraryClick) },
                    onDeleteClick = { viewModel.onEvent(IdeaDetailsEvent.OnDeleteClick) },
                )

                IdeaDetailsTab.Discussion -> CommentInputBar(
                    value = state.commentInput,
                    onValueChange = { viewModel.onEvent(IdeaDetailsEvent.OnCommentChange(it)) },
                    onSend = { viewModel.onEvent(IdeaDetailsEvent.OnSendComment) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            IdeaDetailsTabs(
                selectedTab = state.selectedTab,
                commentsCount = state.commentsCount,
                onTabSelected = { viewModel.onEvent(IdeaDetailsEvent.OnTabSelected(it)) }
            )

            when (state.selectedTab) {
                IdeaDetailsTab.Details -> IdeaDetailsContent(
                    state = state,
                    modifier = Modifier.weight(1f)
                )

                IdeaDetailsTab.Discussion -> IdeaDiscussionContent(
                    state = state,
                    onRetryComment = { localId ->
                        viewModel.onEvent(IdeaDetailsEvent.OnRetryComment(localId))
                    },
                    onDeletePendingComment = { localId ->
                        viewModel.onEvent(IdeaDetailsEvent.OnDeletePendingComment(localId))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IdeaDetailsTabs(
    selectedTab: IdeaDetailsTab,
    commentsCount: Int,
    onTabSelected: (IdeaDetailsTab) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = TextPrimary
    ) {
        Tab(
            selected = selectedTab == IdeaDetailsTab.Details,
            onClick = { onTabSelected(IdeaDetailsTab.Details) },
            text = {
                Text(
                    text = stringResource(R.string.idea_details_tab_details),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
        Tab(
            selected = selectedTab == IdeaDetailsTab.Discussion,
            onClick = { onTabSelected(IdeaDetailsTab.Discussion) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
                ) {
                    Text(
                        text = stringResource(R.string.idea_details_tab_discussion),
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (commentsCount > 0) {
                        DiscussionBadge(count = commentsCount)
                    }
                }
            }
        )
    }
}

@Composable
private fun DiscussionBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(PrimaryBlue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = WhiteCards
        )
    }
}

@Composable
private fun IdeaDetailsContent(
    state: IdeaDetailsState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
    ) {
        Spacer(Modifier.height(CoTripTokens.spacing.x1))

        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        CoTripCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            IdeaInfoRow(
                icon = CoTripIcons.Location,
                label = stringResource(R.string.idea_details_city),
                value = state.city
            )
            CoTripDivider()
            IdeaInfoRow(
                icon = CoTripIcons.Link,
                label = stringResource(R.string.idea_details_link),
                value = state.link.ifBlank { stringResource(R.string.activity_details_empty) },
                valueColor = if (state.link.isBlank()) TextSecondary else PrimaryBlue
            )
            CoTripDivider()
            IdeaInfoRow(
                icon = CoTripIcons.AccountBalance,
                label = stringResource(R.string.idea_details_cost),
                value = state.cost
            )
            CoTripDivider()
            IdeaInfoRow(
                icon = CoTripIcons.Info,
                label = stringResource(R.string.idea_details_notes),
                value = state.notes
            )
        }

        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun IdeaInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x1_5
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor
            )
        }
    }
}

@Composable
private fun IdeaDiscussionContent(
    state: IdeaDetailsState,
    onRetryComment: (String) -> Unit,
    onDeletePendingComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
    LaunchedEffect(state.discussion.size) {
        if (state.discussion.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(
            horizontal = CoTripTokens.spacing.x2,
            vertical = CoTripTokens.spacing.x2
        ),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
    ) {
        items(
            items = state.discussion,
            key = {
                when (it) {
                    is IdeaDiscussionItemUi.Message -> it.id
                    is IdeaDiscussionItemUi.System -> it.id
                }
            }
        ) { item ->
            when (item) {
                is IdeaDiscussionItemUi.Message -> {
                    if (item.isMe) {
                        MyMessageBubble(
                            item = item,
                            onRetry = { localId -> onRetryComment(localId) },
                            onDeletePending = { localId -> onDeletePendingComment(localId) }
                        )
                    } else {
                        OtherMessageBubble(item = item)
                    }
                }

                is IdeaDiscussionItemUi.System -> SystemMessage(item = item)
            }
        }
    }
}

@Composable
private fun MyMessageBubble(
    item: IdeaDiscussionItemUi.Message,
    onRetry: (String) -> Unit,
    onDeletePending: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = PrimaryLight
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(
                        horizontal = CoTripTokens.spacing.x2,
                        vertical = CoTripTokens.spacing.x1_5
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                if (!item.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.photoUrl,
                        contentDescription = item.author,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = item.initials,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
        }
        Spacer(Modifier.height(CoTripTokens.spacing.x0_5))
        Text(
            text = item.time,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        when (item.deliveryState) {
            IdeaDiscussionItemUi.DeliveryState.Sent -> Unit
            IdeaDiscussionItemUi.DeliveryState.Sending -> {
                Text(
                    text = stringResource(R.string.idea_details_comment_sending),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            IdeaDiscussionItemUi.DeliveryState.Failed -> {
                Text(
                    text = stringResource(R.string.idea_details_comment_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
                ) {
                    item.localId?.let { localId ->
                        TextButton(onClick = { onRetry(localId) }) {
                            Text(text = stringResource(R.string.idea_details_retry_comment))
                        }
                        TextButton(
                            onClick = { onDeletePending(localId) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Error)
                        ) {
                            Text(text = stringResource(R.string.idea_details_delete_pending_comment))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherMessageBubble(
    item: IdeaDiscussionItemUi.Message,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            if (!item.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.photoUrl,
                    contentDescription = item.author,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = item.initials,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Border)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(
                        horizontal = CoTripTokens.spacing.x2,
                        vertical = CoTripTokens.spacing.x1_5
                    )
                )
            }
            Text(
                text = item.time,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SystemMessage(
    item: IdeaDiscussionItemUi.System,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(CoTripTokens.spacing.x0_5))
        Text(
            text = item.time,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun DetailsBottomBar(
    addedDay: Int?,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val buttonText = if (addedDay == null) {
                stringResource(R.string.idea_details_add_to_itinerary)
            } else {
                stringResource(R.string.idea_details_added_to_day, addedDay)
            }
            PrimaryButton(
                text = buttonText,
                onClick = onAddClick,
                enabled = addedDay == null,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = onDeleteClick,
                colors = ButtonDefaults.textButtonColors(contentColor = Error)
            ) {
                Text(text = stringResource(R.string.idea_details_delete))
            }
        }
    }
}

@Composable
private fun CommentInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x1_5
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.idea_details_add_comment),
                    color = TextSecondary
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank()
                ) {
                    Icon(
                        imageVector = CoTripIcons.Share,
                        contentDescription = null,
                        tint = if (value.isNotBlank()) PrimaryBlue else TextSecondary
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Border,
                unfocusedIndicatorColor = Border,
                disabledIndicatorColor = Border,
                cursorColor = PrimaryBlue
            )
        )
    }
}
