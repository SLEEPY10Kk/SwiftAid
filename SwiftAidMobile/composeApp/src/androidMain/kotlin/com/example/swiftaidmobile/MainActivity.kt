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

    // Current coords — updated on every GPS fix, used by crash detection
    var currentLat = 0.0
    var currentLon = 0.0

    // Prevent duplicate syncs within the same app session
    private var hasSynced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(this)

        if (!locationHelper.hasPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
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

        // DUMMY TESTING: Force a sync with specific coordinates
        if (BuildConfig.DEBUG) {
            // Replace these with the coordinates you want to test
            val dummyLat = 28.6139 // e.g., Delhi
            val dummyLon = 77.2090
            Log.d("RoadSOS", "Testing with dummy location: ($dummyLat, $dummyLon)")
            performSyncAndDisplay(dummyLat, dummyLon)
        }

        // Immediate sync with last known location (fast, may be slightly stale)
        locationHelper.getLastLocation { lat, lon ->
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Last known location: $lat, $lon")
            performSyncAndDisplay(lat, lon)
        }

        // Live updates — ensures sync fires when fresh GPS fix arrives
        locationCallback = locationHelper.startLiveUpdates { lat, lon ->
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Live location: $lat, $lon")
            performSyncAndDisplay(lat, lon)
            PoiSyncWorker.schedule(this@MainActivity, lat, lon)
        }
    }

    private fun performSyncAndDisplay(lat: Double, lon: Double) {
        currentLat = lat
        currentLon = lon

        // Skip if already synced this session or location is invalid
        if (hasSynced || (lat == 0.0 && lon == 0.0)) return
        hasSynced = true

        lifecycleScope.launch {
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Sync starting for ($lat, $lon)")

            val repo = PoiRepository(this@MainActivity)

            if (!repo.isCacheValid()) {
                if (BuildConfig.DEBUG) Log.d("RoadSOS", "Cache invalid — syncing from server...")
                repo.syncFromServer(lat, lon)
            } else {
                if (BuildConfig.DEBUG) Log.d("RoadSOS", "Cache valid — using existing data")
            }

            val allPois       = repo.getNearestPois(200)
            val nearestByType = repo.getNearestByEmergencyType()

            if (BuildConfig.DEBUG) {
                Log.d("RoadSOS", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("RoadSOS", "Total POIs in DB : ${allPois.size}")
                Log.d("RoadSOS", "Emergency services (nearest per type):")
                nearestByType.forEach { (type, poi) ->
                    Log.d("RoadSOS", "  [$type] ${poi.name}")
                    Log.d("RoadSOS", "         ${poi.distance_m}m | ${poi.address}")
                    Log.d("RoadSOS", "         phone: ${poi.phone ?: "not available"}")
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