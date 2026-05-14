package com.example.swiftaid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.atomic.AtomicBoolean

data class SosLocationResult(
    val location: Location?,
    val source: String
)

class LocationFallbackCascade(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val handler = Handler(Looper.getMainLooper())

    fun fetch(onComplete: (SosLocationResult) -> Unit) {
        if (!hasLocationPermission()) {
            onComplete(SosLocationResult(null, SOURCE_PERMISSION_MISSING))
            return
        }

        requestCurrentLocation(
            priority = Priority.PRIORITY_HIGH_ACCURACY,
            timeoutMs = GPS_TIMEOUT_MS,
            source = SOURCE_GPS
        ) { gpsResult ->
            if (gpsResult.location != null) {
                onComplete(gpsResult)
            } else {
                requestCurrentLocation(
                    priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    timeoutMs = BALANCED_TIMEOUT_MS,
                    source = SOURCE_BALANCED
                ) { balancedResult ->
                    if (balancedResult.location != null) {
                        onComplete(balancedResult)
                    } else {
                        requestCachedLocation(onComplete)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(
        priority: Int,
        timeoutMs: Long,
        source: String,
        onComplete: (SosLocationResult) -> Unit
    ) {
        val completed = AtomicBoolean(false)
        val cancellation = CancellationTokenSource()
        val timeoutRunnable = Runnable {
            if (completed.compareAndSet(false, true)) {
                cancellation.cancel()
                onComplete(SosLocationResult(null, "$source timeout"))
            }
        }

        handler.postDelayed(timeoutRunnable, timeoutMs)

        runCatching {
            fusedLocationClient.getCurrentLocation(priority, cancellation.token)
                .addOnSuccessListener { location ->
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable)
                        onComplete(SosLocationResult(location, if (location != null) source else "$source null"))
                    }
                }
                .addOnFailureListener { throwable ->
                    Log.w(TAG, "Location attempt failed: $source", throwable)
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable)
                        onComplete(SosLocationResult(null, "$source failed"))
                    }
                }
        }.onFailure { throwable ->
            Log.w(TAG, "Location attempt could not start: $source", throwable)
            if (completed.compareAndSet(false, true)) {
                handler.removeCallbacks(timeoutRunnable)
                onComplete(SosLocationResult(null, "$source unavailable"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCachedLocation(onComplete: (SosLocationResult) -> Unit) {
        runCatching {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    onComplete(SosLocationResult(location, if (location != null) SOURCE_CACHE else SOURCE_UNAVAILABLE))
                }
                .addOnFailureListener { throwable ->
                    Log.w(TAG, "Cached location failed", throwable)
                    onComplete(SosLocationResult(null, SOURCE_UNAVAILABLE))
                }
        }.onFailure { throwable ->
            Log.w(TAG, "Cached location unavailable", throwable)
            onComplete(SosLocationResult(null, SOURCE_UNAVAILABLE))
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "LocationFallbackCascade"
        private const val GPS_TIMEOUT_MS = 3_000L
        private const val BALANCED_TIMEOUT_MS = 2_000L
        private const val SOURCE_GPS = "GPS high accuracy"
        private const val SOURCE_BALANCED = "Cell/Wi-Fi balanced"
        private const val SOURCE_CACHE = "Last known cache"
        private const val SOURCE_UNAVAILABLE = "Location unavailable"
        private const val SOURCE_PERMISSION_MISSING = "Location permission missing"
    }
}
