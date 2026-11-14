package com.example.cyberpunk.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyberpunk.R
import com.example.cyberpunk.data.CapsuleStatsCalculator
import com.example.cyberpunk.data.FutureUnlockItem
import com.example.cyberpunk.data.HeatmapDayCell
import com.example.cyberpunk.data.MonthlyActivityBar
import com.example.cyberpunk.data.TagDistributionSlice
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoBorderGradient
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.CapsuleUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TagPalette = listOf(
    TokyoPrimary,
    TokyoSecondary,
    TokyoAccent,
    TokyoSuccess,
    TokyoError
)

@Composable
fun StatsScreen(
    uiState: CapsuleUiState,
    onBackClick: () -> Unit
) {
    val summary = remember(uiState.myCapsules, uiState.currentUserId) {
        CapsuleStatsCalculator.compute(
            capsules = uiState.myCapsules,
            currentUserId = uiState.currentUserId
        )
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
    }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "statsEntryAnim"
    )

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header + Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_screen_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.stats_screen_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                )
            }
            OutlinedButton(
                onClick = onBackClick,
                border = BorderStroke(1.dp, TokyoPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = TokyoPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_close),
                    color = TokyoPrimary
                )
            }
        }

        // Streak Badge
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = TokyoSurface,
            border = BorderStroke(1.dp, TokyoBorderGradient),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Серия активности по месяцам: ${summary.kpi.activeMonthStreak}"
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(TokyoAccent.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = TokyoAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.stats_streak_badge, summary.kpi.activeMonthStreak),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TokyoOnBackground
                        )
                    )
                }

                Text(
                    text = "Всего: ${summary.kpi.totalCount}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = TokyoAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        if (summary.kpi.totalCount == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TokyoSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.stats_empty_banner),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // 1. KPI 2x2 Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiStatCard(
                    label = stringResource(R.string.stats_kpi_sealed),
                    value = summary.kpi.sealedCount,
                    accentColor = TokyoSecondary,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    label = stringResource(R.string.stats_kpi_opened),
                    value = summary.kpi.openedCount,
                    accentColor = TokyoSuccess,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiStatCard(
                    label = stringResource(R.string.stats_kpi_received),
                    value = summary.kpi.receivedCount,
                    accentColor = TokyoAccent,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    label = stringResource(R.string.stats_kpi_sent),
                    value = summary.kpi.sentCount,
                    accentColor = TokyoPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. Heatmap over 6 months
        HeatmapSectionCard(
            heatmapWeeks = summary.heatmapWeeks,
            animProgress = animProgress
        )

        // 3. Monthly Bar Chart
        MonthlyBarsSectionCard(
            monthlyBars = summary.monthlyBars,
            animProgress = animProgress
        )

        // 4. Tag Donut Chart
        TagDonutSectionCard(
            tagSlices = summary.tagSlices,
            animProgress = animProgress
        )

        // 5. Future Unlocks Timeline
        FutureUnlocksSectionCard(
            futureUnlocks = summary.futureUnlocks,
            dateFormatter = dateFormatter,
            animProgress = animProgress
        )
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: $value"
            },
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TokyoOnSurface,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun HeatmapSectionCard(
    heatmapWeeks: List<List<HeatmapDayCell>>,
    animProgress: Float
) {
    val activeDaysCount = remember(heatmapWeeks) {
        heatmapWeeks.sumOf { week -> week.count { it.count > 0 } }
    }
    val totalEventsCount = remember(heatmapWeeks) {
        heatmapWeeks.sumOf { week -> week.sumOf { it.count } }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TokyoPrimary.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_heatmap_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TokyoOnBackground
                )
            )
            Text(
                text = stringResource(R.string.stats_heatmap_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .semantics {
                        contentDescription =
                            "Тепловая карта активности за 6 месяцев: активных дней $activeDaysCount, событий $totalEventsCount"
                    }
            ) {
                val cols = heatmapWeeks.size.coerceAtLeast(1)
                val rows = 7
                val spacing = 3.dp.toPx()
                val cellWidth = ((size.width - spacing * (cols - 1)) / cols).coerceAtLeast(2f)
                val cellHeight = ((size.height - spacing * (rows - 1)) / rows).coerceAtLeast(2f)
                val cellSize = minOf(cellWidth, cellHeight)
                val gridWidth = cols * cellSize + (cols - 1) * spacing
                val startX = ((size.width - gridWidth) * 0.5f).coerceAtLeast(0f)

                heatmapWeeks.forEachIndexed { colIndex, week ->
                    week.forEachIndexed { rowIndex, cell ->
                        val baseColor = when (cell.intensityLevel) {
                            0 -> TokyoBackground.copy(alpha = 0.75f)
                            1 -> TokyoPrimary.copy(alpha = 0.35f * animProgress)
                            2 -> TokyoPrimary.copy(alpha = 0.60f * animProgress)
                            3 -> TokyoSecondary.copy(alpha = 0.80f * animProgress)
                            else -> TokyoAccent.copy(alpha = 0.98f * animProgress)
                        }
                        val x = startX + colIndex * (cellSize + spacing)
                        val y = rowIndex * (cellSize + spacing)
                        drawRoundRect(
                            color = baseColor,
                            topLeft = Offset(x, y),
                            size = Size(cellSize, cellSize),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyBarsSectionCard(
    monthlyBars: List<MonthlyActivityBar>,
    animProgress: Float
) {
    val descText = remember(monthlyBars) {
        val details = monthlyBars.joinToString(", ") { "${it.monthLabel}: ${it.count}" }
        "График активности по месяцам: $details"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TokyoSecondary.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_monthly_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TokyoOnBackground
                )
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics {
                        contentDescription = descText
                    }
            ) {
                val count = monthlyBars.size.coerceAtLeast(1)
                val slotWidth = size.width / count.toFloat()
                val barWidth = (slotWidth * 0.52f).coerceAtLeast(8f)
                val maxBarHeight = (size.height - 12.dp.toPx()).coerceAtLeast(10f)
                val minStubHeight = 6.dp.toPx()

                // Baseline
                drawLine(
                    color = TokyoOnSurface.copy(alpha = 0.25f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                monthlyBars.forEachIndexed { index, item ->
                    val animatedRatio = (item.heightFraction * animProgress).coerceIn(0f, 1f)
                    val barHeight = if (item.count == 0) {
                        minStubHeight
                    } else {
                        (animatedRatio * maxBarHeight).coerceAtLeast(minStubHeight * 1.6f)
                    }
                    val left = index * slotWidth + (slotWidth - barWidth) * 0.5f
                    val top = size.height - barHeight
                    val brush = if (item.count > 0) {
                        Brush.verticalGradient(
                            colors = listOf(TokyoAccent, TokyoPrimary, TokyoSecondary),
                            startY = top,
                            endY = size.height
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                TokyoBackground.copy(alpha = 0.8f),
                                TokyoBackground.copy(alpha = 0.8f)
                            )
                        )
                    }
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                monthlyBars.forEach { item ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.count.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (item.count > 0) TokyoAccent else TokyoOnSurface,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = item.monthLabel,
                            style = MaterialTheme.typography.labelSmall.copy(color = TokyoOnSurface)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagDonutSectionCard(
    tagSlices: List<TagDistributionSlice>,
    animProgress: Float
) {
    val descText = remember(tagSlices) {
        if (tagSlices.isEmpty()) {
            "Распределение по тегам: тегов пока нет"
        } else {
            val parts = tagSlices.joinToString(", ") { "${it.tag} (${it.count} шт., ${it.percentage}%)" }
            "Кольцевая диаграмма тегов: $parts"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TokyoAccent.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_tags_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TokyoOnBackground
                )
            )

            if (tagSlices.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_tags_empty),
                    style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface),
                    modifier = Modifier.semantics {
                        contentDescription = descText
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(130.dp)
                            .semantics {
                                contentDescription = descText
                            }
                    ) {
                        val strokeWidth = 18.dp.toPx()
                        val diameter = minOf(size.width, size.height) - strokeWidth
                        val topLeft = Offset(
                            (size.width - diameter) * 0.5f,
                            (size.height - diameter) * 0.5f
                        )
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = TokyoBackground.copy(alpha = 0.7f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )

                        var currentStartAngle = -90f
                        val totalSweep = 360f * animProgress
                        tagSlices.forEachIndexed { idx, slice ->
                            val sweep = (slice.fraction * totalSweep).coerceAtLeast(2f)
                            val color = TagPalette[idx % TagPalette.size]
                            drawArc(
                                color = color,
                                startAngle = currentStartAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            currentStartAngle += sweep
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tagSlices.forEachIndexed { idx, slice ->
                            val color = TagPalette[idx % TagPalette.size]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(color, CircleShape)
                                    )
                                    Text(
                                        text = slice.tag,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TokyoOnBackground,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${slice.count} (${slice.percentage}%)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TokyoOnSurface)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FutureUnlocksSectionCard(
    futureUnlocks: List<FutureUnlockItem>,
    dateFormatter: DateTimeFormatter,
    animProgress: Float
) {
    val descText = remember(futureUnlocks) {
        if (futureUnlocks.isEmpty()) {
            "Линия будущих открытий: нет ожидающих капсул"
        } else {
            val items = futureUnlocks.joinToString(", ") { "${it.title} (через ${it.daysRemaining} дн.)" }
            "Линия будущих открытий: $items"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TokyoSuccess.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_future_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TokyoOnBackground
                )
            )

            if (futureUnlocks.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_future_empty),
                    style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface),
                    modifier = Modifier.semantics {
                        contentDescription = descText
                    }
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .semantics {
                            contentDescription = descText
                        }
                ) {
                    val padX = 16.dp.toPx()
                    val usableWidth = (size.width - padX * 2f).coerceAtLeast(1f)
                    val centerY = size.height * 0.5f

                    drawLine(
                        color = TokyoPrimary.copy(alpha = 0.35f),
                        start = Offset(padX, centerY),
                        end = Offset(padX + usableWidth * animProgress, centerY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    futureUnlocks.forEachIndexed { idx, item ->
                        val cx = padX + item.timelinePosition.coerceIn(0f, 1f) * usableWidth * animProgress
                        val dotColor = if (idx == 0) TokyoAccent else TokyoSecondary
                        drawCircle(
                            color = dotColor.copy(alpha = 0.28f),
                            radius = 11.dp.toPx(),
                            center = Offset(cx, centerY)
                        )
                        drawCircle(
                            color = dotColor,
                            radius = 5.5.dp.toPx(),
                            center = Offset(cx, centerY)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    futureUnlocks.forEachIndexed { idx, item ->
                        val formattedDate = runCatching {
                            dateFormatter.format(Instant.ofEpochMilli(item.unlockDateMillis))
                        }.getOrDefault("")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TokyoBackground.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (idx == 0) TokyoAccent else TokyoOnBackground
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TokyoOnSurface)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.stats_days_remaining_format, item.daysRemaining),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = TokyoSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
