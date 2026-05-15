package com.example.swiftaidmobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback

class AndroidLocationManager(private val context: Context) : LocationManager {
    private val locationHelper = LocationHelper(context)
    private var locationCallback: LocationCallback? = null

    override fun hasPermission(): Boolean = locationHelper.hasPermission()

    override fun requestPermission() {
        val activity = context as? Activity ?: return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            1001
        )
    }

    override fun startLocationUpdates(onLocationResult: (Location) -> Unit) {
        locationCallback = locationHelper.startLiveUpdates { lat, lon ->
            onLocationResult(Location(lat, lon))
        }
    }

    override fun stopLocationUpdates() {
        locationCallback?.let {
            locationHelper.stopLiveUpdates(it)
            locationCallback = null
        }
    }
}

@Composable
actual fun rememberLocationManager(): LocationManager {
    val context = LocalContext.current
    val locationManager = remember(context) { AndroidLocationManager(context) }
    
    DisposableEffect(locationManager) {
        onDispose {
            locationManager.stopLocationUpdates()
        }
    }
    
    return locationManager
}
