package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CapsuleStageDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CapsuleStagesTimeline(
    capsule: CapsuleDto?,
    onBackClick: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.timeline_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TokyoPrimary
                )
                if (capsule != null) {
                    Text(
                        text = capsule.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TokyoAccent
                    )
                }
            }
            OutlinedButton(
                onClick = onBackClick,
                border = BorderStroke(1.dp, TokyoPrimary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TokyoPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_back_to_radar),
                    color = TokyoPrimary
                )
            }
        }

        val stages = capsule?.stages?.sortedBy { it.stageNumber }.orEmpty()

        val currentStageNumber = if (capsule?.isFullyUnlocked == true) {
            // When fully unlocked, highlight the highest unlocked stage (the open message)
            stages.lastOrNull { it.isUnlocked }?.stageNumber ?: stages.firstOrNull()?.stageNumber
        } else {
            // When locked, highlight the first stage awaiting unlock
            stages.firstOrNull { !it.isUnlocked }?.stageNumber ?: stages.firstOrNull()?.stageNumber
        }

        stages.forEachIndexed { index, stage ->
            TimelineStageRow(
                stage = stage,
                formattedDate = dateFormatter.format(Instant.ofEpochMilli(stage.unlockDate)),
                isCurrent = stage.stageNumber == currentStageNumber,
                isLast = index == stages.lastIndex
            )
        }
    }
}

@Composable
private fun TimelineStageRow(
    stage: CapsuleStageDto,
    formattedDate: String,
    isCurrent: Boolean,
    isLast: Boolean
) {
    val statusColor = if (stage.isUnlocked) TokyoSuccess else TokyoError
    val borderColor = if (isCurrent) TokyoAccent else statusColor.copy(alpha = 0.55f)
    val borderWidth = if (isCurrent) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Vertical timeline track & node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 38.dp else 34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrent) TokyoAccent.copy(alpha = 0.25f) else statusColor.copy(alpha = 0.2f)
                    )
                    .then(
                        if (isCurrent) Modifier.border(2.dp, TokyoAccent, CircleShape) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (stage.isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isCurrent) TokyoAccent else statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(if (isCurrent) 3.dp else 2.dp)
                        .fillMaxHeight()
                        .background(
                            if (isCurrent) TokyoAccent.copy(alpha = 0.6f) else statusColor.copy(alpha = 0.4f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            border = BorderStroke(borderWidth, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.stage_header_format, stage.stageNumber),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) TokyoAccent else TokyoOnBackground
                        )
                        if (isCurrent) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = TokyoAccent.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, TokyoAccent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = TokyoAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.stage_badge_current),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TokyoAccent
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, statusColor)
                    ) {
                        Text(
                            text = if (stage.isUnlocked) {
                                stringResource(R.string.stage_status_unlocked)
                            } else {
                                stringResource(R.string.stage_status_locked)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.stage_unlock_date_format, formattedDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = TokyoAccent
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = TokyoBackground
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stage.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (stage.isUnlocked) TokyoOnBackground else TokyoError,
                            fontWeight = if (stage.isUnlocked) FontWeight.Normal else FontWeight.SemiBold
                        )
                        if (!stage.isUnlocked) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.stage_locked_hint_format, formattedDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = TokyoOnSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

