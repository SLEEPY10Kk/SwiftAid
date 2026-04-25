package com.example.swiftaidmobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.swiftaidmobile.db.PoiRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val repo = PoiRepository(this@MainActivity)

            // 1. Sync from server (example coordinates)
            repo.syncFromServer(23.02, 72.57)

            // 2. Read back from DB
            val pois = repo.getNearestPois(5)

            // 3. Print to Logcat
            pois.forEach {
                Log.d("RoadSOS", "${it.name} — ${it.distance_m}m — ${it.type}")
            }

            Log.d("RoadSOS", "Total POIs in DB: ${pois.size}")
        }

        setContent {
            App()
        }
    }

    // In your crash detection
    private fun onCrashDetected() {
        lifecycleScope.launch {
            val pois = PoiRepository(this@MainActivity).getNearestPois(5)
            if (pois.isNotEmpty()) {
                // showEmergencyUI(pois.first())
            } else {
                // callServerFallback()
            }
        }
    }
}
