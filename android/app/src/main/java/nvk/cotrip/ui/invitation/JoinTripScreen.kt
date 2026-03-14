package nvk.cotrip.ui.invitation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
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
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripTextField
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinTripScreen(
    viewModel: JoinTripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val inviteErrorText = if (state.inviteInput.isNotBlank() && !state.isInviteValid) {
        stringResource(R.string.join_trip_invalid)
    } else {
        null
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is JoinTripEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT).show()
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
                        icon = CoTripIcons.ArrowBack,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(JoinTripEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.join_trip_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    horizontal = CoTripTokens.spacing.x2,
                    vertical = CoTripTokens.spacing.x2
                ),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
        ) {
            Text(
                text = stringResource(R.string.join_trip_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            CoTripTextField(
                value = state.inviteInput,
                onValueChange = { viewModel.onEvent(JoinTripEvent.OnInviteInputChange(it)) },
                label = stringResource(R.string.join_trip_invite_label),
                placeholder = stringResource(R.string.join_trip_invite_placeholder),
                errorText = inviteErrorText,
                singleLine = true
            )

            Spacer(Modifier.height(CoTripTokens.spacing.x1))

            PrimaryButton(
                text = stringResource(R.string.join_trip_action),
                onClick = { viewModel.onEvent(JoinTripEvent.OnJoinClick) },
                enabled = state.isInviteValid && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
