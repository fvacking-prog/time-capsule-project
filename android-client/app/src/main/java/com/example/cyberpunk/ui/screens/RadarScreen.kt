package com.example.cyberpunk.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.cyberpunk.R
import com.example.cyberpunk.data.MockCapsuleRepository
import com.example.cyberpunk.ui.components.Capsule3DView
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.CapsuleUiState
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.ParticipantStatusDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarScreen(
    uiState: CapsuleUiState,
    onPermissionResult: (Boolean) -> Unit,
    onSelectCapsule: (String) -> Unit,
    onCapsule3DClick: () -> Unit,
    onDismissLockedDialog: () -> Unit,
    onRealHeartbeatTick: suspend (String, String) -> Unit,
    onDemoForceCheckInClick: () -> Unit,
    onDemoHeartbeatTick: suspend (String, Boolean) -> Unit,
    onRefreshClick: () -> Unit
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            onPermissionResult(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val capsule = uiState.selectedCapsule
    val capsuleId = capsule?.id
    val userId = uiState.currentUserId
    val isDemoMode = uiState.isDemoMode
    val isDemoForceActive = uiState.isDemoForceActive
    val lifecycleOwner = LocalLifecycleOwner.current

    val haptic = LocalHapticFeedback.current
    var previousUnlockedState by remember(capsule?.id) { mutableStateOf(capsule?.isFullyUnlocked) }
    val neonFlashAnim = remember { Animatable(0f) }

    LaunchedEffect(capsule?.id, capsule?.isFullyUnlocked) {
        val currentUnlocked = capsule?.isFullyUnlocked
        if (previousUnlockedState == false && currentUnlocked == true) {
            // Haptic vibration feedback on unlock transition
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            // Neon splash animation
            neonFlashAnim.snapTo(0f)
            neonFlashAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            neonFlashAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
            )
        }
        previousUnlockedState = currentUnlocked
    }

    // Real-data mode heartbeat loop (every 15 seconds while screen is STARTED and isDemoMode == false)
    LaunchedEffect(capsuleId, userId, isDemoMode, uiState.hasLocationPermission, lifecycleOwner) {
        if (!isDemoMode && capsuleId != null && userId != null && uiState.hasLocationPermission) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    onRealHeartbeatTick(capsuleId, userId)
                    delay(15_000L)
                }
            }
        }
    }

    // Demo mode heartbeat loop (every 15 seconds while screen is STARTED and Demo Force Check-in is active)
    LaunchedEffect(capsuleId, isDemoMode, isDemoForceActive, lifecycleOwner) {
        if (isDemoMode && isDemoForceActive && capsuleId != null) {
            var firstTick = true
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    onDemoHeartbeatTick(capsuleId, firstTick)
                    firstTick = false
                    delay(15_000L)
                }
            }
        }
    }

    if (uiState.lockedDialogMissingText != null) {
        AlertDialog(
            onDismissRequest = onDismissLockedDialog,
            containerColor = TokyoSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TokyoError
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.access_locked_dialog_title),
                    color = TokyoError,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.access_locked_message, uiState.lockedDialogMissingText),
                    color = TokyoOnBackground
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissLockedDialog) {
                    Text(
                        text = stringResource(R.string.btn_ok),
                        color = TokyoPrimary
                    )
                }
            }
        )
    }

    if (uiState.isLoading && capsule == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TokyoBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = TokyoPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.loading_data),
                    color = TokyoOnSurface
                )
            }
        }
        return
    }

    if (capsule == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TokyoBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.empty_capsules),
                    color = TokyoOnSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRefreshClick) {
                    Text(stringResource(R.string.btn_refresh))
                }
            }
        }
        return
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Capsule selector chips if more than 1 capsule exists
        if (uiState.capsules.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.capsules.forEach { item ->
                    FilterChip(
                        selected = item.id == capsule.id,
                        onClick = { onSelectCapsule(item.id) },
                        label = { Text(item.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TokyoPrimary.copy(alpha = 0.22f),
                            selectedLabelColor = TokyoPrimary,
                            containerColor = TokyoSurface,
                            labelColor = TokyoOnSurface
                        )
                    )
                }
            }
        }

        // Permission banner if location permission was denied
        if (!uiState.hasLocationPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TokyoSurface),
                border = BorderStroke(1.dp, TokyoError.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.location_permission_required_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TokyoError
                    )
                    Text(
                        text = stringResource(R.string.location_permission_required_msg),
                        style = MaterialTheme.typography.bodySmall,
                        color = TokyoOnSurface
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TokyoPrimary,
                            contentColor = TokyoBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_grant_permission))
                    }
                }
            }
        }

        val flashAlpha = neonFlashAnim.value
        val cardBorderColor = when {
            flashAlpha > 0.01f -> TokyoAccent.copy(alpha = (0.5f + flashAlpha * 0.5f).coerceIn(0f, 1f))
            capsule.isFullyUnlocked -> TokyoSuccess.copy(alpha = 0.6f)
            else -> TokyoError.copy(alpha = 0.45f)
        }
        val cardBorderWidth = if (flashAlpha > 0.01f) (1.dp + 3.dp * flashAlpha) else 1.dp

        // 1. 3D Capsule Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            border = BorderStroke(
                width = cardBorderWidth,
                color = cardBorderColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = capsule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TokyoOnBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (capsule.isFullyUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (capsule.isFullyUnlocked) TokyoSuccess else TokyoError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (capsule.isFullyUnlocked) {
                                stringResource(R.string.capsule_status_unlocked)
                            } else {
                                stringResource(R.string.capsule_status_locked)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (capsule.isFullyUnlocked) TokyoSuccess else TokyoError
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Capsule3DView(
                        isUnlocked = capsule.isFullyUnlocked,
                        isSyncing = uiState.isSyncing,
                        onCapsuleClick = onCapsule3DClick,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Neon splash flash overlay
                    if (flashAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            TokyoAccent.copy(alpha = 0.65f * flashAlpha),
                                            TokyoSuccess.copy(alpha = 0.35f * flashAlpha),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = TokyoBackground.copy(alpha = 0.88f * flashAlpha),
                                border = BorderStroke(2.dp, TokyoAccent.copy(alpha = flashAlpha))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = TokyoAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.capsule_neon_unlocked_banner),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TokyoAccent
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (capsule.isFullyUnlocked) {
                        stringResource(R.string.capsule_tap_unlocked_hint)
                    } else {
                        stringResource(R.string.capsule_tap_locked_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (capsule.isFullyUnlocked) TokyoSuccess else TokyoOnSurface
                )
            }
        }

        // 2. Animated Radar Canvas & Heartbeat / Demo Controls
        AnimatedGeoRadarCard(
            capsule = capsule,
            uiState = uiState,
            onDemoForceCheckInClick = onDemoForceCheckInClick,
            onOpenTimelineClick = onCapsule3DClick,
            onRefreshClick = onRefreshClick
        )

        // 3. Participants Status List
        ParticipantsStatusCard(
            participants = capsule.participants,
            currentUserId = uiState.currentUserId
        )
    }
}

private val StaticRadarRingStroke = Stroke(width = 1.5f)
private val PulseRadarRingStroke = Stroke(width = 2.5f)

@Composable
private fun AnimatedGeoRadarCard(
    capsule: CapsuleDto,
    uiState: CapsuleUiState,
    onDemoForceCheckInClick: () -> Unit,
    onOpenTimelineClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulseTransition")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulseWave"
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweepAngle"
    )

    val currentUserParticipant = capsule.participants.find { it.userId == uiState.currentUserId }
    val distanceMeters: Double? = currentUserParticipant?.distanceMeters
        ?: if (uiState.deviceLat != null && uiState.deviceLon != null) {
            MockCapsuleRepository.haversineMeters(
                uiState.deviceLat,
                uiState.deviceLon,
                capsule.latitude,
                capsule.longitude
            )
        } else {
            null
        }

    val radarAccent = if (capsule.isFullyUnlocked) TokyoSuccess else TokyoAccent

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        border = BorderStroke(1.dp, TokyoPrimary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.radar_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TokyoPrimary
                )
                OutlinedButton(
                    onClick = onRefreshClick,
                    border = BorderStroke(1.dp, TokyoPrimary.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TokyoPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_refresh),
                        style = MaterialTheme.typography.labelMedium,
                        color = TokyoPrimary
                    )
                }
            }

            // Expanding concentric circles Radar Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(180.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = min(size.width, size.height) / 2f

                    drawCircle(
                        color = TokyoBackground.copy(alpha = 0.9f),
                        radius = maxRadius,
                        center = center
                    )
                    drawCircle(
                        color = radarAccent.copy(alpha = 0.08f),
                        radius = maxRadius * 0.72f,
                        center = center
                    )

                    // Static range rings
                    for (step in 1..3) {
                        drawCircle(
                            color = TokyoPrimary.copy(alpha = 0.25f),
                            radius = maxRadius * (step / 3f),
                            center = center,
                            style = StaticRadarRingStroke
                        )
                    }

                    // Crosshair lines
                    drawLine(
                        color = TokyoPrimary.copy(alpha = 0.2f),
                        start = Offset(0f, center.y),
                        end = Offset(size.width, center.y),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = TokyoPrimary.copy(alpha = 0.2f),
                        start = Offset(center.x, 0f),
                        end = Offset(center.x, size.height),
                        strokeWidth = 1f
                    )

                    // 3 Expanding animated wave rings
                    for (waveIndex in 0 until 3) {
                        val phase = (pulseProgress + waveIndex / 3f) % 1f
                        val waveRadius = phase * maxRadius
                        val waveAlpha = (1f - phase) * 0.65f
                        drawCircle(
                            color = radarAccent.copy(alpha = waveAlpha),
                            radius = waveRadius,
                            center = center,
                            style = PulseRadarRingStroke
                        )
                    }

                    // Rotating radar sweep beam
                    val sweepEnd = Offset(
                        x = center.x + cos(sweepAngle) * maxRadius,
                        y = center.y + sin(sweepAngle) * maxRadius
                    )
                    drawLine(
                        color = radarAccent.copy(alpha = 0.75f),
                        start = center,
                        end = sweepEnd,
                        strokeWidth = 2.5f
                    )

                    // Participant blips around the center capsule
                    capsule.participants.forEachIndexed { idx, participant ->
                        val angle = (idx * 2.4f) + 0.6f
                        val distRatio = when {
                            participant.isNearbyAndRecent -> 0.14f
                            participant.distanceMeters != null -> {
                                (participant.distanceMeters / (capsule.radiusMeters * 3.0))
                                    .toFloat()
                                    .coerceIn(0.25f, 0.88f)
                            }
                            else -> 0.82f
                        }
                        val blipOffset = Offset(
                            x = center.x + cos(angle) * (maxRadius * distRatio),
                            y = center.y + sin(angle) * (maxRadius * distRatio)
                        )
                        val blipColor = if (participant.isNearbyAndRecent) TokyoSuccess else TokyoError
                        drawCircle(
                            color = blipColor.copy(alpha = 0.35f),
                            radius = 11f,
                            center = blipOffset
                        )
                        drawCircle(
                            color = blipColor,
                            radius = 5.5f,
                            center = blipOffset
                        )
                    }

                    // Center capsule node
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = center
                    )
                }
            }

            // Distance readout
            Text(
                text = if (distanceMeters != null) {
                    stringResource(R.string.radar_distance_meters, distanceMeters, capsule.radiusMeters)
                } else {
                    stringResource(R.string.radar_distance_waiting, capsule.radiusMeters)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TokyoAccent
            )

            // Heartbeat indicator row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val indicatorColor = when {
                    uiState.isSyncing -> TokyoAccent
                    uiState.isDemoMode && uiState.isDemoForceActive -> TokyoSuccess
                    !uiState.isDemoMode && uiState.hasLocationPermission -> TokyoSuccess
                    else -> TokyoSecondary
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val heartbeatText = when {
                    uiState.isSyncing -> stringResource(R.string.heartbeat_syncing_now)
                    uiState.isDemoMode && uiState.isDemoForceActive -> stringResource(R.string.heartbeat_demo_active)
                    uiState.isDemoMode -> stringResource(R.string.heartbeat_demo_idle)
                    else -> stringResource(R.string.heartbeat_real_active)
                }
                Text(
                    text = heartbeatText,
                    style = MaterialTheme.typography.labelMedium,
                    color = TokyoOnSurface
                )
            }

            // Demo Force Check-in Button
            Button(
                onClick = onDemoForceCheckInClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TokyoSecondary,
                    contentColor = TokyoBackground
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_demo_force_checkin),
                    fontWeight = FontWeight.Bold
                )
            }

            if (capsule.isFullyUnlocked) {
                Button(
                    onClick = onOpenTimelineClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokyoSuccess,
                        contentColor = TokyoBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_open_timeline),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantsStatusCard(
    participants: List<ParticipantStatusDto>,
    currentUserId: String?
) {
    val now = System.currentTimeMillis()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        border = BorderStroke(1.dp, TokyoPrimary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.participants_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TokyoPrimary
            )

            participants.forEach { participant ->
                val isMe = participant.userId == currentUserId
                val badgeText = if (participant.isNearbyAndRecent) {
                    val secondsAgo = if (participant.lastSeenAt != null) {
                        ((now - participant.lastSeenAt) / 1000L).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    stringResource(R.string.badge_nearby, secondsAgo)
                } else if (participant.distanceMeters != null) {
                    stringResource(R.string.badge_away_meters, participant.distanceMeters)
                } else {
                    stringResource(R.string.badge_away_unknown)
                }

                val badgeColor = if (participant.isNearbyAndRecent) TokyoSuccess else TokyoError

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = TokyoBackground,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isMe) TokyoAccent.copy(alpha = 0.6f) else badgeColor.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = participant.username,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TokyoOnBackground
                            )
                            Text(
                                text = participant.userId,
                                style = MaterialTheme.typography.labelSmall,
                                color = TokyoOnSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = badgeColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, badgeColor)
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        }
    }
}

