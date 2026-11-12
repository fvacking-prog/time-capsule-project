package com.example.cyberpunk.ui.screens

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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoBorderGradient
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.CapsuleUiState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    uiState: CapsuleUiState,
    onNavigateToCreate: () -> Unit,
    onNavigateToRadar: () -> Unit,
    onOpenJoinDialog: () -> Unit,
    onNavigateToCapsules: () -> Unit
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    // Find closest sealed capsule
    val sealedCapsules = uiState.myCapsules.filter { it.status == "SEALED" || !it.isFullyUnlocked }
    val closestCapsule = sealedCapsules.minByOrNull { cap ->
        cap.stages.firstOrNull()?.unlockDate ?: Long.MAX_VALUE
    }
    val closestUnlockDate = closestCapsule?.stages?.firstOrNull()?.unlockDate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Заголовок и подзаголовок
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(TokyoAccent, CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TokyoOnSurface
                )
            )
        }

        // Главная карточка: Запечатать новое воспоминание
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TokyoBorderGradient, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(TokyoPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = TokyoPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(TokyoAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TokyoAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.home_seal_btn),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoOnBackground
                    )
                )

                Text(
                    text = stringResource(R.string.home_seal_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TokyoOnSurface
                    )
                )

                ElevatedButton(
                    onClick = onNavigateToCreate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = TokyoPrimary,
                        contentColor = TokyoBackground
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_seal_btn),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Карточка: Ближайшее открытие
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TokyoBorderGradient, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(TokyoSecondary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = TokyoSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_closest_unlock_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TokyoSecondary
                        )
                    )
                }

                if (closestCapsule != null && closestUnlockDate != null) {
                    val remainingMs = maxOf(0L, closestUnlockDate - currentTimeMillis)
                    val days = remainingMs / (24 * 60 * 60 * 1000L)
                    val hours = (remainingMs / (60 * 60 * 1000L)) % 24
                    val minutes = (remainingMs / (60 * 1000L)) % 60
                    val seconds = (remainingMs / 1000L) % 60

                    Text(
                        text = closestCapsule.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TokyoOnBackground
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountdownUnitBox(value = days.toString(), label = "дней")
                        CountdownUnitBox(value = hours.toString().padStart(2, '0'), label = "часов")
                        CountdownUnitBox(value = minutes.toString().padStart(2, '0'), label = "мин")
                        CountdownUnitBox(value = seconds.toString().padStart(2, '0'), label = "сек")
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_closest_unlock_empty),
                        style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface)
                    )
                }
            }
        }

        // Карточка: Присоединиться по коду
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenJoinDialog() }
                .border(1.dp, TokyoAccent.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(TokyoAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = TokyoAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.home_card_join_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TokyoOnBackground
                            )
                        )
                        Text(
                            text = stringResource(R.string.home_card_join_desc),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TokyoOnSurface
                            )
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenJoinDialog,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TokyoAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.btn_join_by_code))
                }
            }
        }

        // Быстрый вход в Радар для geo-капсул
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToRadar() }
                .border(1.dp, TokyoSuccess.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(TokyoSuccess.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = TokyoSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.home_quick_radar_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TokyoOnBackground
                            )
                        )
                        Text(
                            text = stringResource(R.string.home_quick_radar_desc),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TokyoOnSurface
                            )
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToRadar,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TokyoSuccess),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.nav_radar))
                }
            }
        }
    }
}

@Composable
private fun CountdownUnitBox(
    value: String,
    label: String
) {
    Column(
        modifier = Modifier
            .background(TokyoBackground.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TokyoPrimary
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TokyoOnSurface,
                fontSize = 10.sp
            )
        )
    }
}
