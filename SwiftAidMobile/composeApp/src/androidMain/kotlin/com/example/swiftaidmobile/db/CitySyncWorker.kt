package com.example.swiftaidmobile.db

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class CitySyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val city = inputData.getString("city") ?: return Result.failure()
        val lat = inputData.getDouble("lat", 0.0)
        val lon = inputData.getDouble("lon", 0.0)

        PoiRepository(applicationContext).syncCityOffline(city, lat, lon)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, city: String, lat: Double, lon: Double) {
            val input = workDataOf(
                "city" to city,
                "lat" to lat,
                "lon" to lon
            )

            // Refresh every 24 hours
            val work = PeriodicWorkRequestBuilder<CitySyncWorker>(24, TimeUnit.HOURS)
                .setInputData(input)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED) // Prefer WiFi
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "city_sync_$city",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}
