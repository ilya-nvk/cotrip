package nvk.cotrip.ui.auth

import android.content.Context
import android.content.pm.Signature
import android.os.Build
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
import nvk.cotrip.util.AppLogger
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private const val TAG = "SignInScreen"

@Composable
fun SignInScreen(
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
    val missingClientIdMessage = stringResource(R.string.sign_in_error_missing_google_client_id)

    val signInClient = remember(context, serverClientId) {
        serverClientId.takeIf { it.isNotBlank() }?.let { clientId ->
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, options)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onEvent(SignInEvent.OnGoogleSignInResult(result.resultCode, result.data))
    }

    LaunchedEffect(serverClientId) {
        val defaultWebClientId = runCatching {
            val id = context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )
            if (id == 0) "<missing default_web_client_id>" else context.getString(id)
        }.getOrElse { "<missing default_web_client_id>" }
        val fingerprints = runCatching { loadSigningFingerprints(context) }
            .getOrElse { error -> "failed_to_read_fingerprints: ${error.message}" }
        AppLogger.w(
            TAG,
            "Google config debug: buildConfigClientId='$serverClientId', " +
                "defaultWebClientId='$defaultWebClientId', package='${context.packageName}', " +
                "buildType='${BuildConfig.BUILD_TYPE}', debug=${BuildConfig.DEBUG}"
        )
        AppLogger.w(TAG, "Google config debug: app signing fingerprints -> $fingerprints")
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
                            AppLogger.e(
                                TAG,
                                "Google sign-in unavailable: empty GOOGLE_SERVER_CLIENT_ID"
                            )
                            viewModel.onEvent(
                                SignInEvent.OnGoogleSignInFailed(missingClientIdMessage)
                            )
                            return@PrimaryButton
                        }
                        val client = signInClient
                        if (client == null) {
                            AppLogger.e(
                                TAG,
                                "Google sign-in unavailable: signInClient is null. " +
                                    "buildConfigClientId='$serverClientId'"
                            )
                            viewModel.onEvent(
                                SignInEvent.OnGoogleSignInFailed(missingClientIdMessage)
                            )
                            return@PrimaryButton
                        }
                        viewModel.onEvent(SignInEvent.StartGoogleSignIn)
                        launcher.launch(client.signInIntent)
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

private fun loadSigningFingerprints(context: Context): String {
    val signatures = readAppSignatures(context)
    if (signatures.isEmpty()) return "none"
    return signatures.joinToString(separator = " | ") { signature ->
        val certificate = decodeX509Certificate(signature)
        val encoded = certificate.encoded
        val sha1 = digestToHexWithColons("SHA-1", encoded)
        val sha256 = digestToHexWithColons("SHA-256", encoded)
        "SHA1=$sha1 SHA256=$sha256"
    }
}

@Suppress("DEPRECATION")
private fun readAppSignatures(context: Context): List<Signature> {
    val packageManager = context.packageManager
    val packageName = context.packageName
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val packageInfo = packageManager.getPackageInfo(
            packageName,
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        )
        packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
    } else {
        val packageInfo = packageManager.getPackageInfo(
            packageName,
            android.content.pm.PackageManager.GET_SIGNATURES
        )
        packageInfo.signatures?.toList().orEmpty()
    }
}

private fun decodeX509Certificate(signature: Signature): X509Certificate {
    val certificateFactory = CertificateFactory.getInstance("X509")
    val input = ByteArrayInputStream(signature.toByteArray())
    return certificateFactory.generateCertificate(input) as X509Certificate
}

private fun digestToHexWithColons(algorithm: String, bytes: ByteArray): String {
    val digest = MessageDigest.getInstance(algorithm).digest(bytes)
    return digest.joinToString(":") { byte -> "%02x".format(byte) }
}
