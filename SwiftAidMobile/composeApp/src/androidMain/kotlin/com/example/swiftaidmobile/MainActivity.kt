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

    private var hasSynced = false

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
        if (BuildConfig.DEBUG) Log.d("RoadSOS", "startLocationAndSync called")

        // 1. Try immediate sync with last known location
        locationHelper.getLastLocation { lat, lon ->
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Last known location obtained: $lat, $lon")
            performSyncAndDisplay(lat, lon)
        }

        // 2. Also start live updates to ensure sync happens as soon as a fix is acquired
        locationCallback = locationHelper.startLiveUpdates { lat, lon ->
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Live location obtained: $lat, $lon")
            performSyncAndDisplay(lat, lon)
            
            // Schedule background worker with real coords
            PoiSyncWorker.schedule(this@MainActivity, lat, lon)
        }
    }

    private fun performSyncAndDisplay(lat: Double, lon: Double) {
        // Always update global coords for crash detection
        currentLat = lat
        currentLon = lon

        // Only proceed with the sync check if we have a valid location and haven't synced yet.
        // This avoids blocking the sync if the first fix is an invalid (0.0, 0.0) point.
        if (hasSynced || (lat == 0.0 && lon == 0.0)) return
        hasSynced = true

        lifecycleScope.launch {
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Location fix acquired ($lat, $lon). Checking sync status...")
            val repo = PoiRepository(this@MainActivity)
            val cacheValid = repo.isCacheValid()

            if (!cacheValid) {
                if (BuildConfig.DEBUG) Log.d("RoadSOS", "Cache invalid, starting sync...")
                repo.syncFromServer(lat, lon)
            } else {
                if (BuildConfig.DEBUG) Log.d("RoadSOS", "Cache still valid, using existing data.")
            }

            // Always fetch and display results once we have confirmed our sync state
            val allPois = repo.getNearestPois(200)
            val nearestByType = repo.getNearestByEmergencyType()

            if (BuildConfig.DEBUG) {
                Log.d("RoadSOS", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("RoadSOS", "Sync location: $lat, $lon")
                Log.d("RoadSOS", "Total POIs in DB: ${allPois.size}")
                Log.d("RoadSOS", "Nearest Emergency Services:")
                nearestByType.forEach { (type, poi) ->
                    Log.d("RoadSOS", "  [$type] ${poi.name} — ${poi.distance_m}m")
                }
                Log.d("RoadSOS", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
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
