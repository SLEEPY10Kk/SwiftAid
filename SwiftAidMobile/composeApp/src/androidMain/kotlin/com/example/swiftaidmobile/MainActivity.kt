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

        // Start live location updates
        locationCallback = locationHelper.startLiveUpdates { lat, lon ->
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Location callback fired: $lat, $lon")
            currentLat = lat
            currentLon = lon
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Location Update: $lat, $lon")

            // Sync POI cache with real location
            lifecycleScope.launch {
                if (!hasSynced) {
                    hasSynced = true
                    if (BuildConfig.DEBUG) Log.d("RoadSOS", "Coroutine launched")
                    val repo = PoiRepository(this@MainActivity)
                    val cacheValid = repo.isCacheValid()
                    if (BuildConfig.DEBUG) Log.d("RoadSOS", "Cache valid: $cacheValid")

                    if (!cacheValid) {
                        if (BuildConfig.DEBUG) Log.d("RoadSOS", "Starting sync...")
                        repo.syncFromServer(lat, lon)
                        if (BuildConfig.DEBUG) Log.d("RoadSOS", "Sync complete")

                        val allPois          = repo.getNearestPois(200)
                        val nearestByType    = repo.getNearestByEmergencyType()

                        if (BuildConfig.DEBUG) {
                            // Summary log
                            Log.d("RoadSOS", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.d("RoadSOS", "Total POIs in DB: ${allPois.size}")
                            Log.d("RoadSOS", "Emergency POIs for Routes API:")
                            nearestByType.forEach { (type, poi) ->
                                Log.d("RoadSOS", "  [$type] ${poi.name} — ${poi.distance_m}m — ${poi.lat},${poi.lon}")
                            }
                            Log.d("RoadSOS", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                            Log.d("RoadSOS", "Synced: Found ${nearestByType.size} unique emergency types out of ${allPois.size} total POIs.")
                        }
                    } else {
                        if (BuildConfig.DEBUG) Log.d("RoadSOS", "Cache is still valid, skipping server sync.")
                    }
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
