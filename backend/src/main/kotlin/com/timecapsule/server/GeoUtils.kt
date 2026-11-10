// FILE: GeoUtils.kt
package com.timecapsule.server

import com.timecapsule.shared.ParticipantStatusDto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val RECENT_THRESHOLD_MS = 60_000L
    const val LOCKED_PLACEHOLDER = "[ЗАШИФРОВАНО AES-256-GCM]"

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return EARTH_RADIUS_METERS * c
    }

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    }

    fun isNearbyAndRecent(
        lastLat: Double?,
        lastLon: Double?,
        lastSeenAt: Long?,
        capsuleLat: Double,
        capsuleLon: Double,
        radiusMeters: Double,
        now: Long
    ): Boolean {
        if (lastLat == null || lastLon == null || lastSeenAt == null) return false
        if (!radiusMeters.isFinite() || radiusMeters <= 0.0) return false
        if (!isValidCoordinate(lastLat, lastLon) || !isValidCoordinate(capsuleLat, capsuleLon)) return false
        val elapsed = now - lastSeenAt
        if (elapsed !in 0L..RECENT_THRESHOLD_MS) return false
        val distance = haversineMeters(lastLat, lastLon, capsuleLat, capsuleLon)
        return distance <= radiusMeters
    }

    fun stageText(
        encryptedText: String,
        unlockDate: Long,
        now: Long,
        allParticipantsNearbyAndRecent: Boolean,
        decrypt: (String) -> String = CryptoUtils::decrypt
    ): String {
        return if (now >= unlockDate && allParticipantsNearbyAndRecent) {
            decrypt(encryptedText)
        } else {
            LOCKED_PLACEHOLDER
        }
    }

    fun stageText(
        encryptedText: String,
        unlockDate: Long,
        now: Long,
        participants: List<ParticipantStatusDto>,
        decrypt: (String) -> String = CryptoUtils::decrypt
    ): String {
        val allReady = participants.isNotEmpty() && participants.all { it.isNearbyAndRecent }
        return stageText(
            encryptedText = encryptedText,
            unlockDate = unlockDate,
            now = now,
            allParticipantsNearbyAndRecent = allReady,
            decrypt = decrypt
        )
    }
}