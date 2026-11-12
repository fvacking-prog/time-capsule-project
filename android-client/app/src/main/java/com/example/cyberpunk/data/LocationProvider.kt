package com.example.cyberpunk.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /**
     * Проверяет, включена ли геолокация в системных настройках.
     */
    fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Получение текущих координат с таймаутом 5 секунд:
     * 1. FusedLocationProviderClient (PRIORITY_HIGH_ACCURACY)
     * 2. Fallback на LocationManager (GPS_PROVIDER, затем NETWORK_PROVIDER)
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.Main.immediate) {
        val fusedResult = withTimeoutOrNull(5_000L) {
            getFusedLocation()
        }
        if (fusedResult != null) return@withContext fusedResult

        // Fallback на LocationManager
        withTimeoutOrNull(5_000L) {
            getLocationManagerLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFusedLocation(): Location? {
        val cancellationTokenSource = CancellationTokenSource()
        return try {
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await() ?: fusedClient.lastLocation.await()
        } catch (_: Throwable) {
            try {
                fusedClient.lastLocation.await()
            } catch (_: Throwable) {
                null
            }
        } finally {
            cancellationTokenSource.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationManagerLocation(): Location? {
        val lm = locationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { provider ->
            try {
                lm.isProviderEnabled(provider)
            } catch (_: Throwable) {
                false
            }
        }
        if (providers.isEmpty()) return null

        for (provider in providers) {
            val loc = getSingleLocationFromProvider(lm, provider)
            if (loc != null) return loc
        }

        // Проверяем последние известные координаты
        var bestLastKnown: Location? = null
        for (provider in providers) {
            try {
                val last = lm.getLastKnownLocation(provider)
                if (last != null && (bestLastKnown == null || last.time > bestLastKnown.time)) {
                    bestLastKnown = last
                }
            } catch (_: Throwable) {
            }
        }
        return bestLastKnown
    }

    @SuppressLint("MissingPermission")
    private suspend fun getSingleLocationFromProvider(lm: LocationManager, provider: String): Location? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellationSignal = android.os.CancellationSignal()
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                }
                try {
                    lm.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(appContext)
                    ) { location ->
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                } catch (_: Throwable) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }

        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try {
                        lm.removeUpdates(this)
                    } catch (_: Throwable) {}
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            continuation.invokeOnCancellation {
                try {
                    lm.removeUpdates(listener)
                } catch (_: Throwable) {}
            }

            try {
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: Throwable) {
                try {
                    lm.removeUpdates(listener)
                } catch (_: Throwable) {}
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}
