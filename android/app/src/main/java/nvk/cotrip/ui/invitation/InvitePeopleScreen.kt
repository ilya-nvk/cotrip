package nvk.cotrip.ui.invitation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.components.SecondaryButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextMedium
import nvk.cotrip.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitePeopleScreen(
    viewModel: InvitePeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is InvitePeopleEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
                is InvitePeopleEffect.CopyToClipboard ->
                    clipboardManager.setText(AnnotatedString(effect.text))
                is InvitePeopleEffect.ShareText -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, effect.text)
                    }
                    val chooser = Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.invite_people_share_link)
                    )
                    context.startActivity(chooser)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.Close,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(InvitePeopleEvent.OnCloseClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.invite_people_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        when (val uiState = state) {
            InvitePeopleState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            InvitePeopleState.Unavailable -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = CoTripTokens.spacing.x2),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.invite_people_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMedium
                    )
                    Spacer(modifier = Modifier.height(CoTripTokens.spacing.x2))
                    PrimaryButton(
                        text = stringResource(R.string.invite_people_retry),
                        onClick = { viewModel.onEvent(InvitePeopleEvent.OnRetryClick) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            is InvitePeopleState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = CoTripTokens.spacing.x2),
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
                ) {
                    Text(
                        text = stringResource(R.string.invite_people_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMedium
                    )

                    CoTripCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = CoTripTokens.spacing.x2,
                            vertical = CoTripTokens.spacing.x2
                        ),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Text(
                            text = stringResource(R.string.invite_people_link_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(CoTripTokens.spacing.x1))

                        Text(
                            text = uiState.inviteLink,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.invite_people_expires_hint,
                            uiState.expiresInHours
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    PrimaryButton(
                        text = stringResource(R.string.invite_people_copy_link),
                        leadingIcon = { Icon(CoTripIcons.Copy, contentDescription = null) },
                        onClick = { viewModel.onEvent(InvitePeopleEvent.OnCopyClick) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    SecondaryButton(
                        text = stringResource(R.string.invite_people_share_link),
                        leadingIcon = { Icon(CoTripIcons.Share, contentDescription = null) },
                        onClick = { viewModel.onEvent(InvitePeopleEvent.OnShareClick) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
