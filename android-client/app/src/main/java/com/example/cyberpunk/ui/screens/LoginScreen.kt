package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.cyberpunk.BuildConfig
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.components.Capsule3DView
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    isAuthenticating: Boolean,
    onGoogleIdTokenReceived: (String) -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val missingClientIdMsg = stringResource(R.string.auth_error_missing_client_id)
    val cancelledMsg = stringResource(R.string.auth_error_cancelled)
    val noAccountMsg = stringResource(R.string.auth_error_no_account)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
    ) {
        if (BuildConfig.DEBUG) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_content_desc),
                    tint = TokyoPrimary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Capsule3DView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                isUnlocked = true,
                isSyncing = false,
                onCapsuleClick = {}
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TokyoPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TokyoOnSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Google Sign-In Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = TokyoSurface),
                border = BorderStroke(1.dp, TokyoPrimary.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
                            if (webClientId.isEmpty()) {
                                onGoogleSignInError(missingClientIdMsg)
                                return@Button
                            }
                            coroutineScope.launch {
                                try {
                                    val credentialManager = CredentialManager.create(context)
                                    val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                                        .build()
                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(signInWithGoogleOption)
                                        .build()

                                    val result = credentialManager.getCredential(
                                        request = request,
                                        context = context
                                    )

                                    val credential = result.credential
                                    if (credential is CustomCredential &&
                                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    ) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                        onGoogleIdTokenReceived(googleIdTokenCredential.idToken)
                                    } else {
                                        onGoogleSignInError(
                                            context.getString(R.string.auth_error_generic, credential.type)
                                        )
                                    }
                                } catch (_: GetCredentialCancellationException) {
                                    onGoogleSignInError(cancelledMsg)
                                } catch (_: NoCredentialException) {
                                    onGoogleSignInError(noAccountMsg)
                                } catch (e: GoogleIdTokenParsingException) {
                                    onGoogleSignInError(
                                        context.getString(
                                            R.string.auth_error_generic,
                                            e.localizedMessage ?: "GoogleIdTokenParsingException"
                                        )
                                    )
                                } catch (e: GetCredentialException) {
                                    val err = e.errorMessage?.toString() ?: e.type
                                    val friendlyMsg = when {
                                        err.contains("DEVELOPER_ERROR", ignoreCase = true) || err.contains("code: 10", ignoreCase = true) ->
                                            context.getString(R.string.auth_error_developer_error)
                                        err.contains("NETWORK", ignoreCase = true) || err.contains("7:", ignoreCase = true) ->
                                            context.getString(R.string.auth_error_google_network)
                                        else ->
                                            context.getString(R.string.auth_error_generic, err)
                                    }
                                    onGoogleSignInError(friendlyMsg)
                                } catch (e: Exception) {
                                    onGoogleSignInError(
                                        context.getString(
                                            R.string.auth_error_generic,
                                            e.localizedMessage ?: e.javaClass.simpleName
                                        )
                                    )
                                }
                            }
                        },
                        enabled = !isAuthenticating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TokyoPrimary,
                            contentColor = TokyoBackground
                        )
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = TokyoBackground
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.btn_sign_in_google),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

