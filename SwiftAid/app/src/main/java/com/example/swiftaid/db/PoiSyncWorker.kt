package com.example.swiftaid.db

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class PoiSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val lat = inputData.getDouble("lat", 0.0)
        val lon = inputData.getDouble("lon", 0.0)
        PoiRepository(applicationContext).syncFromServer(lat, lon)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, lat: Double, lon: Double) {
            val input = workDataOf("lat" to lat, "lon" to lon)

            val work = PeriodicWorkRequestBuilder<PoiSyncWorker>(30, TimeUnit.MINUTES)
                .setInputData(input)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "poi_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}