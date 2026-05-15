package com.example.swiftaidmobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getLastLocation(callback: (Double, Double) -> Unit) {
        if (hasPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let { callback(it.latitude, it.longitude) }
                }
            } catch (e: SecurityException) {
                if (BuildConfig.DEBUG) Log.e("RoadSOS", "SecurityException in getLastLocation: ${e.message}")
            }
        }
    }

    fun startLiveUpdates(callback: (Double, Double) -> Unit): LocationCallback {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000)
            .setMinUpdateIntervalMillis(3_000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    callback(it.latitude, it.longitude)
                }
            }
        }

        if (BuildConfig.DEBUG) Log.d("RoadSOS", "hasPermission: ${hasPermission()}")

        if (hasPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                if (BuildConfig.DEBUG) Log.d("RoadSOS", "Location updates started")
            } catch (e: SecurityException) {
                if (BuildConfig.DEBUG) Log.e("RoadSOS", "SecurityException in startLiveUpdates: ${e.message}")
            }
        } else {
            if (BuildConfig.DEBUG) Log.e("RoadSOS", "Permission not granted — location updates NOT started")
        }
        return locationCallback
    }

    fun stopLiveUpdates(callback: LocationCallback) {
        fusedLocationClient.removeLocationUpdates(callback)
    }
}
