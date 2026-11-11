package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.HandleCheckStatus
import com.timecapsule.shared.UserDto

@Composable
fun ChooseHandleScreen(
    currentUser: UserDto?,
    handleInput: String,
    handleStatus: HandleCheckStatus,
    isSaving: Boolean,
    onHandleInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLogout: () -> Unit
) {
    val avatarModel = currentUser?.avatarUrl ?: currentUser?.photoUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!avatarModel.isNullOrBlank()) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = currentUser?.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(TokyoPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TokyoPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.choose_handle_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = TokyoPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.choose_handle_subtitle,
                    currentUser?.username ?: ""
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TokyoOnSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = TokyoSurface),
                border = BorderStroke(1.dp, TokyoPrimary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = handleInput,
                        onValueChange = onHandleInputChange,
                        label = { Text(stringResource(R.string.choose_handle_label)) },
                        placeholder = { Text(stringResource(R.string.choose_handle_placeholder)) },
                        prefix = {
                            Text(
                                text = "@",
                                color = TokyoPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = null,
                                tint = TokyoPrimary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = TokyoBackground,
                            unfocusedContainerColor = TokyoBackground,
                            focusedBorderColor = TokyoPrimary,
                            unfocusedBorderColor = TokyoPrimary.copy(alpha = 0.3f),
                            focusedTextColor = TokyoOnBackground,
                            unfocusedTextColor = TokyoOnBackground
                        )
                    )

                    Text(
                        text = stringResource(R.string.choose_handle_rules),
                        style = MaterialTheme.typography.bodySmall,
                        color = TokyoOnSurface
                    )

                    if (handleStatus != HandleCheckStatus.IDLE) {
                        val (statusColor, statusText) = when (handleStatus) {
                            HandleCheckStatus.CHECKING -> TokyoAccent to stringResource(R.string.handle_status_checking)
                            HandleCheckStatus.FREE -> TokyoSuccess to stringResource(R.string.handle_status_free, handleInput)
                            HandleCheckStatus.TAKEN -> TokyoError to stringResource(R.string.handle_status_taken, handleInput)
                            HandleCheckStatus.RESERVED -> TokyoError to stringResource(R.string.handle_status_reserved, handleInput)
                            HandleCheckStatus.INVALID_FORMAT -> TokyoError to stringResource(R.string.handle_status_invalid_format)
                            HandleCheckStatus.ERROR -> TokyoError to stringResource(R.string.handle_status_error)
                            HandleCheckStatus.IDLE -> TokyoOnSurface to ""
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(statusColor.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            when (handleStatus) {
                                HandleCheckStatus.CHECKING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = TokyoAccent
                                    )
                                }
                                HandleCheckStatus.FREE -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = TokyoSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = TokyoError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            )
                        }
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = handleStatus == HandleCheckStatus.FREE && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TokyoPrimary,
                            contentColor = TokyoBackground
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = TokyoBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.btn_save_handle),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TokyoError),
                border = BorderStroke(1.dp, TokyoError.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_logout))
            }
        }
    }
}

