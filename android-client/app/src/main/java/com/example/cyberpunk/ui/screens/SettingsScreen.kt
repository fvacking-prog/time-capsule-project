package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cyberpunk.BuildConfig
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.CapsuleUiState

@Composable
fun SettingsScreen(
    uiState: CapsuleUiState,
    onOpenServerSettings: () -> Unit,
    onOpenSetPinDialog: () -> Unit,
    onRemovePin: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onExportData: () -> Unit,
    onOpenDeleteDataDialog: () -> Unit,
    onDismissDeleteDataDialog: () -> Unit,
    onConfirmDeleteData: () -> Unit,
    onOpenAboutDialog: (String) -> Unit,
    onDismissAboutDialog: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    // Delete Data Confirmation Dialog
    if (uiState.showConfirmDeleteDataDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDataDialog,
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_data_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoError
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_delete_data_msg),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface)
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteData,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokyoError,
                        contentColor = TokyoBackground
                    )
                ) {
                    Text(stringResource(R.string.delete_all_data_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDataDialog) {
                    Text(stringResource(R.string.btn_cancel), color = TokyoOnSurface)
                }
            },
            containerColor = TokyoSurface
        )
    }

    // About App / Policy / Terms Dialog
    if (uiState.showAboutDialog) {
        val (titleRes, textRes) = when (uiState.aboutDialogType) {
            "PRIVACY" -> R.string.about_privacy_title to R.string.about_privacy_text
            "TERMS" -> R.string.about_terms_title to R.string.about_terms_text
            else -> R.string.about_app_title to R.string.about_app_text
        }

        AlertDialog(
            onDismissRequest = onDismissAboutDialog,
            title = {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(textRes),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface, lineHeight = 20.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = onDismissAboutDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokyoPrimary,
                        contentColor = TokyoBackground
                    )
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            },
            containerColor = TokyoSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Column {
            Text(
                text = stringResource(R.string.settings_screen_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TokyoPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface)
            )
        }

        // БЛОК: ПРОФИЛЬ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_profile_section),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val photoUrl = uiState.currentUser?.photoUrl
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(TokyoPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = TokyoPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentUsername.ifEmpty { "Пользователь" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TokyoOnBackground
                            )
                        )
                        val email = uiState.currentUser?.email
                        if (!email.isNullOrBlank()) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                            )
                        }
                    }

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

        // БЛОК: СЕТЬ (ТОЛЬКО ДЛЯ DEBUG-СБОРКИ)
        if (BuildConfig.DEBUG) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TokyoSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_network_section),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TokyoPrimary
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenServerSettings() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Dns, null, tint = TokyoAccent, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.server_url_label),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnBackground)
                                )
                                Text(
                                    text = uiState.settingsUrlInput,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                                )
                            }
                        }

                        Icon(Icons.Default.ChevronRight, null, tint = TokyoOnSurface)
                    }
                }
            }
        }

        // БЛОК: КОНФИДЕНЦИАЛЬНОСТЬ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_privacy_section),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )

                // PIN lock switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pin_lock_title),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnBackground)
                        )
                        Text(
                            text = stringResource(R.string.pin_lock_desc),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                        )
                    }

                    Switch(
                        checked = uiState.isPinSet,
                        onCheckedChange = { enable ->
                            if (enable) onOpenSetPinDialog() else onRemovePin()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TokyoBackground,
                            checkedTrackColor = TokyoPrimary
                        )
                    )
                }

                // Notifications toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.notifications_toggle_title),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnBackground)
                        )
                        Text(
                            text = stringResource(R.string.notifications_toggle_desc),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                        )
                    }

                    Switch(
                        checked = uiState.isNotificationsEnabled,
                        onCheckedChange = onToggleNotifications,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TokyoBackground,
                            checkedTrackColor = TokyoSecondary
                        )
                    )
                }
            }
        }

        // БЛОК: УПРАВЛЕНИЕ ДАННЫМИ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_data_section),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )

                // Export JSON
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExportData() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Share, null, tint = TokyoAccent, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.export_data_btn),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnBackground)
                        )
                        Text(
                            text = stringResource(R.string.export_data_desc),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TokyoOnSurface)
                }

                // Delete all data
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDeleteDataDialog() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, null, tint = TokyoError, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.delete_all_data_btn),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TokyoError)
                        )
                        Text(
                            text = stringResource(R.string.delete_all_data_desc),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TokyoOnSurface)
                }
            }
        }

        // БЛОК: О ПРИЛОЖЕНИИ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_about_section),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )

                SettingsSimpleRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about_app_title),
                    onClick = { onOpenAboutDialog("ABOUT") }
                )

                SettingsSimpleRow(
                    icon = Icons.Default.Policy,
                    title = stringResource(R.string.about_privacy_title),
                    onClick = { onOpenAboutDialog("PRIVACY") }
                )

                SettingsSimpleRow(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.about_terms_title),
                    onClick = { onOpenAboutDialog("TERMS") }
                )
            }
        }
    }
}

@Composable
private fun SettingsSimpleRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = TokyoPrimary, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnBackground)
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = TokyoOnSurface)
    }
}
