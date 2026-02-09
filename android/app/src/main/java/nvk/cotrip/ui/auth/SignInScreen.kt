package nvk.cotrip.ui.auth

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.BuildConfig
import nvk.cotrip.R
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextMedium
import nvk.cotrip.ui.theme.TextSecondary

@Composable
fun SignInScreen(
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID

    val signInClient = remember(serverClientId) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onEvent(SignInEvent.OnGoogleSignInResult(result.resultCode, result.data))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SignInEffect.ShowToast ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is SignInEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = CoTripTokens.spacing.x4),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(CoTripTokens.spacing.x1_5))

                Text(
                    text = stringResource(R.string.sign_in_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(CoTripTokens.spacing.x4 + CoTripTokens.spacing.x2))

                PrimaryButton(
                    text = if (state.isLoading)
                        stringResource(R.string.signing_in)
                    else
                        stringResource(R.string.continue_with_google),
                    onClick = {
                        if (serverClientId.isBlank()) {
                            viewModel.onEvent(
                                SignInEvent.OnGoogleSignInFailed("Missing Google client id.")
                            )
                            return@PrimaryButton
                        }
                        viewModel.onEvent(SignInEvent.StartGoogleSignIn)
                        launcher.launch(signInClient.signInIntent)
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.googleicon),
                            contentDescription = null,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                )

                Text(
                    text = stringResource(R.string.sign_in_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = CoTripTokens.spacing.x2)
                        .widthIn(max = 320.dp)
                )
            }
        }
    }
}
