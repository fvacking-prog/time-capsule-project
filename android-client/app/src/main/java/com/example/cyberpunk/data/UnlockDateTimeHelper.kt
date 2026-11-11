package com.example.cyberpunk.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

object UnlockDateTimeHelper {
    const val MIN_LEAD_MILLIS = 60_000L

    fun defaultUnlockMillis(
        nowMillis: Long = System.currentTimeMillis(),
        daysAhead: Long = 7L,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val localNow = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .withSecond(0)
            .withNano(0)
        return localNow.plusDays(daysAhead).toInstant().toEpochMilli()
    }

    /**
     * Возвращает ближайшее допустимое локальное время (с точностью до минуты),
     * гарантированно отстоящее минимум на [MIN_LEAD_MILLIS] вперёд от [nowMillis].
     */
    fun earliestValidUnlockDateTime(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ZonedDateTime {
        val minInstant = Instant.ofEpochMilli(nowMillis + MIN_LEAD_MILLIS)
        val zdt = minInstant.atZone(zoneId)
        return if (zdt.second > 0 || zdt.nano > 0) {
            zdt.withSecond(0).withNano(0).plusMinutes(1)
        } else {
            zdt.withSecond(0).withNano(0)
        }
    }

    fun mergePickedUtcDateWithExistingTime(
        utcDateMillis: Long,
        existingMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val pickedDate = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val existingTime = Instant.ofEpochMilli(existingMillis).atZone(zoneId).toLocalTime()
            .withSecond(0)
            .withNano(0)
        val candidate = ZonedDateTime.of(pickedDate, existingTime, zoneId).toInstant().toEpochMilli()
        val earliest = earliestValidUnlockDateTime(nowMillis, zoneId).toInstant().toEpochMilli()
        return maxOf(candidate, earliest)
    }

    fun applyTimeOfDay(
        existingMillis: Long,
        hour: Int,
        minute: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = Instant.ofEpochMilli(existingMillis).atZone(zoneId).toLocalDate()
        return ZonedDateTime.of(date, LocalTime.of(hour, minute, 0, 0), zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun validateMinimumLead(
        targetMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = targetMillis >= nowMillis + MIN_LEAD_MILLIS

    fun chipDateMillis(
        chipKey: String,
        existingMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val currentZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val existingTime = Instant.ofEpochMilli(existingMillis).atZone(zoneId).toLocalTime()
            .withSecond(0)
            .withNano(0)
        val targetDate = when (chipKey) {
            "TOMORROW" -> currentZdt.toLocalDate().plusDays(1)
            "WEEK" -> currentZdt.toLocalDate().plusDays(7)
            "MONTH" -> currentZdt.toLocalDate().plusMonths(1)
            else -> return existingMillis
        }
        val candidate = ZonedDateTime.of(targetDate, existingTime, zoneId).toInstant().toEpochMilli()
        val earliest = earliestValidUnlockDateTime(nowMillis, zoneId).toInstant().toEpochMilli()
        return maxOf(candidate, earliest)
    }
}

