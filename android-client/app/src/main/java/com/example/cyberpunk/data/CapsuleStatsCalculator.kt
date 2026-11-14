package com.example.cyberpunk.data

import com.timecapsule.shared.CapsuleDto
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class CapsuleKpiStats(
    val totalCount: Int,
    val sealedCount: Int,
    val openedCount: Int,
    val receivedCount: Int,
    val sentCount: Int,
    val activeMonthStreak: Int
)

data class MonthlyActivityBar(
    val yearMonth: YearMonth,
    val monthLabel: String,
    val count: Int,
    val heightFraction: Float
)

data class HeatmapDayCell(
    val date: LocalDate,
    val count: Int,
    val intensityLevel: Int // 0..4
)

data class TagDistributionSlice(
    val tag: String,
    val count: Int,
    val fraction: Float,
    val percentage: Int
)

data class FutureUnlockItem(
    val capsuleId: String,
    val title: String,
    val unlockDateMillis: Long,
    val daysRemaining: Long,
    val timelinePosition: Float // 0f..1f
)

data class CapsuleStatsSummary(
    val kpi: CapsuleKpiStats,
    val heatmapWeeks: List<List<HeatmapDayCell>>, // 26 columns x 7 days
    val monthlyBars: List<MonthlyActivityBar>,   // 6 months
    val tagSlices: List<TagDistributionSlice>,   // Top tags
    val futureUnlocks: List<FutureUnlockItem>    // Upcoming unlocks
)

object CapsuleStatsCalculator {

    private const val HEATMAP_WEEKS = 26
    private const val MONTHS_WINDOW = 6
    private const val MAX_TAG_SLICES = 5
    private const val MAX_FUTURE_UNLOCKS = 6
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun compute(
        capsules: List<CapsuleDto>,
        currentUserId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale("ru")
    ): CapsuleStatsSummary {
        val nonArchived = capsules.filter { !it.archived && it.status != "ARCHIVED" }

        val sealedCount = nonArchived.count { c ->
            c.status == "SEALED" || (!c.hasUnlockedStage && c.status != "OPENED" && c.openedAt == null)
        }
        val openedCount = capsules.count { c ->
            c.status == "OPENED" || c.openedAt != null
        }
        val receivedCount = capsules.count { c ->
            isReceivedCapsule(c, currentUserId)
        }
        val sentCount = capsules.count { c ->
            isSentCapsule(c, currentUserId)
        }

        val activityTimestamps = capsules.mapNotNull { c ->
            resolveActivityTimestamp(c)
        }

        val activeYearMonths = mutableSetOf<YearMonth>()
        val dayCounts = mutableMapOf<LocalDate, Int>()
        val monthCounts = mutableMapOf<YearMonth, Int>()

        for (ts in activityTimestamps) {
            val zdt = Instant.ofEpochMilli(ts).atZone(zoneId)
            val date = zdt.toLocalDate()
            val ym = YearMonth.from(zdt)
            activeYearMonths.add(ym)
            dayCounts[date] = (dayCounts[date] ?: 0) + 1
            monthCounts[ym] = (monthCounts[ym] ?: 0) + 1
        }

        // Also record openedAt dates on heatmap and streak if distinct from creation date
        for (c in capsules) {
            val openedTs = c.openedAt ?: continue
            if (openedTs <= 0L) continue
            val openedZdt = Instant.ofEpochMilli(openedTs).atZone(zoneId)
            val openedDate = openedZdt.toLocalDate()
            val openedYm = YearMonth.from(openedZdt)
            activeYearMonths.add(openedYm)
            val createdTs = resolveActivityTimestamp(c)
            val createdDate = createdTs?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            if (createdDate != openedDate) {
                dayCounts[openedDate] = (dayCounts[openedDate] ?: 0) + 1
            }
        }

        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val currentYm = YearMonth.from(nowZdt)
        val today = nowZdt.toLocalDate()

        val streak = calculateMonthStreak(activeYearMonths, currentYm)

        val kpi = CapsuleKpiStats(
            totalCount = capsules.size,
            sealedCount = sealedCount,
            openedCount = openedCount,
            receivedCount = receivedCount,
            sentCount = sentCount,
            activeMonthStreak = streak
        )

        // 1. 6-month bar chart
        val rawMonths = (MONTHS_WINDOW - 1 downTo 0).map { offset ->
            currentYm.minusMonths(offset.toLong())
        }
        val maxMonthCount = rawMonths.maxOfOrNull { monthCounts[it] ?: 0 } ?: 0
        val safeMaxMonth = maxMonthCount.coerceAtLeast(1)
        val monthlyBars = rawMonths.map { ym ->
            val count = monthCounts[ym] ?: 0
            val rawLabel = ym.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                .replace(".", "")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            val fraction = if (maxMonthCount == 0) 0f else (count.toFloat() / safeMaxMonth.toFloat()).coerceIn(0f, 1f)
            MonthlyActivityBar(
                yearMonth = ym,
                monthLabel = rawLabel.take(3),
                count = count,
                heightFraction = fraction
            )
        }

        // 2. 6-month heatmap (26 weeks x 7 days ending on today)
        val totalDays = HEATMAP_WEEKS * 7
        val startDate = today.minusDays((totalDays - 1).toLong())
        val maxDayCount = (0 until totalDays).maxOfOrNull { idx ->
            dayCounts[startDate.plusDays(idx.toLong())] ?: 0
        } ?: 0
        val safeMaxDay = maxDayCount.coerceAtLeast(1)

        val heatmapWeeks = (0 until HEATMAP_WEEKS).map { weekIdx ->
            (0 until 7).map { dayIdx ->
                val date = startDate.plusDays((weekIdx * 7 + dayIdx).toLong())
                val count = dayCounts[date] ?: 0
                val level = when {
                    count <= 0 || maxDayCount <= 0 -> 0
                    count >= maxDayCount -> 4
                    else -> ((count.toFloat() / safeMaxDay.toFloat()) * 3f).roundToInt().coerceIn(1, 3)
                }
                HeatmapDayCell(
                    date = date,
                    count = count,
                    intensityLevel = level
                )
            }
        }

        // 3. Tag distribution (Top 5)
        val tagFrequency = mutableMapOf<String, Int>()
        for (c in capsules) {
            for (rawTag in c.tags) {
                val clean = rawTag.trim().removePrefix("#").lowercase(locale)
                if (clean.isNotEmpty()) {
                    tagFrequency[clean] = (tagFrequency[clean] ?: 0) + 1
                }
            }
        }
        val sortedTags = tagFrequency.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_TAG_SLICES)
        val totalTopTagOccurrences = sortedTags.sumOf { it.value }
        val safeTagTotal = totalTopTagOccurrences.coerceAtLeast(1)
        val tagSlices = if (totalTopTagOccurrences == 0) {
            emptyList()
        } else {
            sortedTags.map { (tag, count) ->
                val fraction = (count.toFloat() / safeTagTotal.toFloat()).coerceIn(0f, 1f)
                TagDistributionSlice(
                    tag = "#$tag",
                    count = count,
                    fraction = fraction,
                    percentage = (fraction * 100f).roundToInt().coerceIn(0, 100)
                )
            }
        }

        // 4. Future unlocks timeline
        val upcomingPairs = nonArchived
            .filter { c -> c.openedAt == null && c.status != "OPENED" }
            .mapNotNull { c ->
                val nextStageUnlock = c.stages
                    .filter { !it.isUnlocked }
                    .minOfOrNull { it.unlockDate }
                    ?: c.stages.minOfOrNull { it.unlockDate }
                if (nextStageUnlock != null && nextStageUnlock > nowMillis) {
                    c to nextStageUnlock
                } else {
                    null
                }
            }
            .sortedBy { it.second }
            .take(MAX_FUTURE_UNLOCKS)

        val minUnlock = upcomingPairs.minOfOrNull { it.second } ?: nowMillis
        val maxUnlock = upcomingPairs.maxOfOrNull { it.second } ?: nowMillis
        val unlockSpan = (maxUnlock - minUnlock).coerceAtLeast(0L)

        val futureUnlocks = upcomingPairs.mapIndexed { index, (cap, unlockMillis) ->
            val remainingMs = (unlockMillis - nowMillis).coerceAtLeast(0L)
            val daysRemaining = (remainingMs + DAY_MILLIS - 1L) / DAY_MILLIS
            val position = when {
                upcomingPairs.size <= 1 -> 0.5f
                unlockSpan <= 0L -> index.toFloat() / (upcomingPairs.size - 1).coerceAtLeast(1).toFloat()
                else -> ((unlockMillis - minUnlock).toFloat() / unlockSpan.toFloat()).coerceIn(0f, 1f)
            }
            FutureUnlockItem(
                capsuleId = cap.id,
                title = cap.title,
                unlockDateMillis = unlockMillis,
                daysRemaining = daysRemaining,
                timelinePosition = position
            )
        }

        return CapsuleStatsSummary(
            kpi = kpi,
            heatmapWeeks = heatmapWeeks,
            monthlyBars = monthlyBars,
            tagSlices = tagSlices,
            futureUnlocks = futureUnlocks
        )
    }

    internal fun resolveActivityTimestamp(capsule: CapsuleDto): Long? {
        if (capsule.createdAt > 0L) return capsule.createdAt
        if (capsule.openedAt != null && capsule.openedAt > 0L) return capsule.openedAt
        val earliestStage = capsule.stages.minOfOrNull { it.unlockDate }
        if (earliestStage != null && earliestStage > 0L) return earliestStage
        return null
    }

    internal fun calculateMonthStreak(
        activeMonths: Set<YearMonth>,
        currentMonth: YearMonth
    ): Int {
        if (activeMonths.isEmpty()) return 0
        var cursor = when {
            activeMonths.contains(currentMonth) -> currentMonth
            activeMonths.contains(currentMonth.minusMonths(1)) -> currentMonth.minusMonths(1)
            else -> return 0
        }
        var streak = 0
        while (activeMonths.contains(cursor)) {
            streak += 1
            cursor = cursor.minusMonths(1)
        }
        return streak
    }

    private fun isReceivedCapsule(capsule: CapsuleDto, currentUserId: String?): Boolean {
        if (capsule.isInbox) return true
        if (currentUserId != null && capsule.creatorId != null && capsule.creatorId != currentUserId) {
            return true
        }
        return false
    }

    private fun isSentCapsule(capsule: CapsuleDto, currentUserId: String?): Boolean {
        if (capsule.isInbox) return false
        val isCreatedByMe = currentUserId == null || capsule.creatorId == null || capsule.creatorId == currentUserId
        if (!isCreatedByMe) return false
        val hasOtherParticipants = capsule.participants.any { p ->
            val uid = currentUserId ?: capsule.creatorId
            uid != null && p.userId != uid
        }
        return hasOtherParticipants || capsule.isGroupCapsule || !capsule.recipient.isNullOrBlank()
    }
}
