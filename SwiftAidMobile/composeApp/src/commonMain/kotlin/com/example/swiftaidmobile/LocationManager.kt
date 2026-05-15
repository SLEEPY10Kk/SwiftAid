package com.example.swiftaidmobile

import androidx.compose.runtime.Composable

data class Location(val latitude: Double, val longitude: Double)

interface LocationManager {
    fun hasPermission(): Boolean
    fun requestPermission()
    fun startLocationUpdates(onLocationResult: (Location) -> Unit)
    fun stopLocationUpdates()
}

@Composable
expect fun rememberLocationManager(): LocationManager