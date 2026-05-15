package com.example.swiftaidmobile

import androidx.compose.runtime.*

class IosLocationManager : LocationManager {
    override fun hasPermission(): Boolean = false // TODO: Implement iOS location permissions
    override fun requestPermission() {}
    override fun startLocationUpdates(onLocationResult: (Location) -> Unit) {}
    override fun stopLocationUpdates() {}
}

@Composable
actual fun rememberLocationManager(): LocationManager {
    return remember { IosLocationManager() }
}
