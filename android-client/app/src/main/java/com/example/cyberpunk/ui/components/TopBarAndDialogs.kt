package com.example.cyberpunk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.timecapsule.shared.UserDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCapsuleTopBar(
    currentUsername: String,
    currentPhotoUrl: String?,
    isDemoMode: Boolean,
    onProfileClick: () -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TokyoPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            AssistChip(
                onClick = onProfileClick,
                label = {
                    Text(
                        text = if (currentUsername.isNotBlank()) {
                            stringResource(R.string.profile_chip_format, currentUsername)
                        } else {
                            stringResource(R.string.profile_select_prompt)
                        },
                        color = TokyoAccent,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    if (!currentPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentPhotoUrl,
                            contentDescription = currentUsername,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TokyoAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = TokyoBackground
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = TokyoAccent.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.demo_mode_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDemoMode) TokyoSuccess else TokyoOnSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isDemoMode,
                    onCheckedChange = onDemoModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TokyoBackground,
                        checkedTrackColor = TokyoSuccess,
                        uncheckedThumbColor = TokyoOnSurface,
                        uncheckedTrackColor = TokyoBackground
                    )
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_content_desc),
                    tint = TokyoPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TokyoSurface,
            titleContentColor = TokyoOnBackground
        )
    )
}

@Composable
fun ProfileSelectionDialog(
    currentUser: UserDto?,
    onSelectDemoUser: (String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TokyoSurface,
        title = {
            Text(
                text = stringResource(R.string.profile_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TokyoPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (currentUser != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = TokyoBackground),
                        border = BorderStroke(1.dp, TokyoPrimary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!currentUser.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentUser.photoUrl,
                                    contentDescription = currentUser.username,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = TokyoAccent,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentUser.username,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TokyoOnBackground
                                )
                                if (!currentUser.email.isNullOrBlank()) {
                                    Text(
                                        text = stringResource(R.string.profile_email_format, currentUser.email),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TokyoAccent
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.profile_id_format, currentUser.id),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TokyoOnSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = TokyoOnSurface.copy(alpha = 0.2f))

                Text(
                    text = stringResource(R.string.profile_switch_demo_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = TokyoOnSurface
                )

                ProfileOptionCard(
                    name = stringResource(R.string.user_maga_name),
                    description = stringResource(R.string.user_maga_desc),
                    isSelected = currentUser?.id == stringResource(R.string.user_maga_id),
                    accentColor = TokyoPrimary,
                    onClick = { onSelectDemoUser("user_maga") }
                )
                ProfileOptionCard(
                    name = stringResource(R.string.user_zainab_name),
                    description = stringResource(R.string.user_zainab_desc),
                    isSelected = currentUser?.id == stringResource(R.string.user_zainab_id),
                    accentColor = TokyoSecondary,
                    onClick = { onSelectDemoUser("user_zainab") }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokyoError,
                        contentColor = TokyoBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_logout),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_close),
                    color = TokyoAccent
                )
            }
        }
    )
}

@Composable
private fun ProfileOptionCard(
    name: String,
    description: String,
    isSelected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = TokyoBackground
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) TokyoSuccess else accentColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TokyoOnBackground
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TokyoOnSurface
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TokyoSuccess
                )
            }
        }
    }
}

@Composable
fun ServerSettingsDialog(
    urlInput: String,
    isChecking: Boolean,
    healthText: String?,
    healthSuccess: Boolean?,
    onUrlChange: (String) -> Unit,
    onCheckHealth: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TokyoSurface,
        title = {
            Text(
                text = stringResource(R.string.server_settings_title),
                color = TokyoPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChange,
                    label = { Text(stringResource(R.string.server_url_label)) },
                    placeholder = { Text(stringResource(R.string.server_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCheckHealth,
                        enabled = !isChecking,
                        border = BorderStroke(1.dp, TokyoAccent)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TokyoAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.btn_check_connection),
                            color = TokyoAccent
                        )
                    }
                }

                if (healthText != null) {
                    val statusColor = when (healthSuccess) {
                        true -> TokyoSuccess
                        false -> TokyoError
                        null -> TokyoAccent
                    }
                    Text(
                        text = healthText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TokyoPrimary,
                    contentColor = TokyoBackground
                )
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    color = TokyoOnSurface
                )
            }
        }
    )
}

@Composable
fun JoinCapsuleDialog(
    codeInput: String,
    isJoining: Boolean,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TokyoSurface,
        title = {
            Text(
                text = stringResource(R.string.dialog_join_title),
                color = TokyoPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.dialog_join_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TokyoOnSurface
                )
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(R.string.dialog_join_input_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_join_input_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onJoin,
                enabled = codeInput.isNotBlank() && !isJoining,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TokyoSuccess,
                    contentColor = TokyoBackground
                )
            ) {
                if (isJoining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = TokyoBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(stringResource(R.string.btn_join_capsule))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    color = TokyoOnSurface
                )
            }
        }
    )
}
