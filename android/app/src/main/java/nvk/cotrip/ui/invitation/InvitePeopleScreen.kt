package nvk.cotrip.ui.invitation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.components.SecondaryButton
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

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is InvitePeopleEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
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
            TopAppBar(
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
                contentPadding = PaddingValues(
                    horizontal = CoTripTokens.spacing.x2,
                    vertical = CoTripTokens.spacing.x2
                )
            ) {
                Text(
                    text = stringResource(R.string.invite_people_link_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                Spacer(Modifier.height(CoTripTokens.spacing.x1))

                Text(
                    text = state.inviteLink,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(CoTripTokens.spacing.x2))

                Text(
                    text = stringResource(
                        R.string.invite_people_expires_hint,
                        state.expiresInHours
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(CoTripTokens.spacing.x1))

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