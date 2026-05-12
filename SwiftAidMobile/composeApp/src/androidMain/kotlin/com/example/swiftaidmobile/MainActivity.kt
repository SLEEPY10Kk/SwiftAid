package com.example.swiftaidmobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.swiftaidmobile.db.PoiRepository
import com.example.swiftaidmobile.db.PoiSyncWorker
import com.google.android.gms.location.LocationCallback
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var locationHelper: LocationHelper
    private var locationCallback: LocationCallback? = null

    // Store current coords — used by crash detection and sync
    private var currentLat = 0.0
    private var currentLon = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(this)

        // Request permission if not granted
        if (!locationHelper.hasPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            startLocationAndSync()
        }

        setContent {
            App()
        }
    }

    // Called after user grants/denies permission
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationAndSync()
        }
    }

    private fun startLocationAndSync() {
        // Start live location updates
        locationCallback = locationHelper.startLiveUpdates { lat, lon ->
            currentLat = lat
            currentLon = lon
            Log.d("RoadSOS", "Location Update: $lat, $lon")

            // Sync POI cache with real location
            lifecycleScope.launch {
                val repo = PoiRepository(this@MainActivity)
                if (!repo.isCacheValid()) {
                    repo.syncFromServer(lat, lon)
                    
                    // Read back from DB (increased limit to find more potential matches)
                    val allPois = repo.getNearestPois(50)

                    // Filter for specific emergency types
                    val emergencyTypes = setOf("hospital", "fire_station", "police")
                    val filteredPois = allPois.filter { it.type.lowercase() in emergencyTypes }

                    // Print to Logcat
                    filteredPois.forEach {
                        Log.d("RoadSOS", "Emergency POI: ${it.name} — ${it.distance_m}m — ${it.type}")
                    }

                    Log.d("RoadSOS", "Synced: Found ${filteredPois.size} emergency POIs out of ${allPois.size} total.")
                } else {
                    Log.d("RoadSOS", "Cache is still valid, skipping server sync.")
                }
            }

            // Schedule background worker with real coords
            PoiSyncWorker.schedule(this@MainActivity, lat, lon)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { locationHelper.stopLiveUpdates(it) }
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST = 1001
    }
}
