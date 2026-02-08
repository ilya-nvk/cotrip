package nvk.cotrip.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripDivider
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.Error
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.PrimaryLight
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.WarningText

private const val KEY_PROFILE = "profile"
private const val KEY_NOTIFICATION_TITLE = "notifications_title"
private const val KEY_DANGER_ZONE = "danger_zone"
private const val KEY_BOTTOM = "bottom"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SettingsEffect.ShowToastRes -> {
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        DeleteProfileDialog(
            onDismiss = { viewModel.onEvent(SettingsEvent.OnDismissDeleteDialog) },
            onConfirm = { viewModel.onEvent(SettingsEvent.OnConfirmDeleteProfileClick) }
        )
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
                        onClick = { viewModel.onEvent(SettingsEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = CoTripTokens.spacing.x2),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
        ) {
            item(key = KEY_PROFILE) {
                ProfileSection(
                    profile = state.profile,
                    onNameChange = { viewModel.onEvent(SettingsEvent.OnNameChange(it)) },
                    onChangePhoto = { viewModel.onEvent(SettingsEvent.OnChangePhotoClick) },
                    onRemovePhoto = { viewModel.onEvent(SettingsEvent.OnRemovePhotoClick) }
                )
            }

            item(key = KEY_NOTIFICATION_TITLE) {
                SectionHeader(
                    title = stringResource(R.string.settings_notifications),
                    modifier = Modifier.padding(horizontal = CoTripTokens.spacing.x2)
                )
            }

            items(
                items = state.notificationSections,
                key = { it.title }
            ) { section ->
                NotificationSection(
                    section = section,
                    onToggle = { key, enabled ->
                        viewModel.onEvent(SettingsEvent.OnToggleNotifications(key, enabled))
                    }
                )
            }

            item(key = KEY_DANGER_ZONE) {
                DangerZone(
                    onLogout = { viewModel.onEvent(SettingsEvent.OnLogoutClick) },
                    onDeleteProfile = { viewModel.onEvent(SettingsEvent.OnDeleteProfileClick) }
                )
            }

            item(key = KEY_BOTTOM) {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ProfileSection(
    profile: SettingsProfileUi,
    onNameChange: (String) -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
    ) {
        SectionHeader(title = stringResource(R.string.settings_profile))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ProfileAvatar(profile = profile)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionText(
                icon = CoTripIcons.PhotoCamera,
                text = stringResource(R.string.settings_change_photo),
                onClick = onChangePhoto,
                color = PrimaryBlue
            )

            if (profile.hasPhoto) {
                Spacer(Modifier.width(CoTripTokens.spacing.x2))
                ActionText(
                    icon = CoTripIcons.Close,
                    text = stringResource(R.string.settings_remove_photo),
                    onClick = onRemovePhoto,
                    color = WarningText
                )
            }
        }

        OutlinedTextField(
            value = profile.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            label = { Text(text = stringResource(R.string.settings_name_label)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Border,
                unfocusedIndicatorColor = Border,
                cursorColor = PrimaryBlue
            )
        )

        Text(
            text = stringResource(R.string.settings_name_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = CoTripTokens.spacing.x0_5)
        )

        CoTripDivider(modifier = Modifier.padding(top = CoTripTokens.spacing.x1))
    }
}

@Composable
private fun ProfileAvatar(profile: SettingsProfileUi) {
    val brush = if (profile.hasPhoto) {
        Brush.linearGradient(
            colors = listOf(PrimaryBlue, PrimaryLight)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(PrimaryBlue, PrimaryBlue)
        )
    }

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = profile.initials,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color,
) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(CoTripTokens.spacing.x0_5))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

@Composable
private fun NotificationSection(
    section: SettingsNotificationSectionUi,
    onToggle: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        SectionHeader(title = section.title)
        section.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { enabled -> onToggle(item.key, enabled) }
                )
            }
        }
    }
}

@Composable
private fun DangerZone(
    onLogout: () -> Unit,
    onDeleteProfile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
    ) {
        CoTripDivider()
        SectionHeader(title = stringResource(R.string.settings_danger_zone))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            Text(text = stringResource(R.string.settings_log_out))
        }

        OutlinedButton(
            onClick = onDeleteProfile,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, Error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
        ) {
            Text(text = stringResource(R.string.settings_delete_profile))
        }
    }
}

@Composable
private fun DeleteProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_delete_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_delete_dialog_message),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = TextPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Error)
            ) {
                Text(
                    text = stringResource(R.string.settings_delete_profile),
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    )
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = modifier
    )
}
