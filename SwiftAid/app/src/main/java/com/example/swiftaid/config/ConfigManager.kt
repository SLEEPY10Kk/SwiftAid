package com.example.swiftaid.config

import android.content.Context
import android.util.Log
import com.example.swiftaid.logging.CrashDataUploader
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ConfigManager(
    context: Context,
    private val configUrl: String = CrashDataUploader.SERVER_BASE_URL + "/config",
    private val repository: ThresholdRepository = ThresholdRepository(context)
) {
    private val running = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null

    fun startPeriodicFetch(
        initialDelaySeconds: Long = 0L,
        periodSeconds: Long = DEFAULT_FETCH_PERIOD_SECONDS
    ) {
        if (!running.compareAndSet(false, true)) return

        repository.applyCurrentToNative()
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "SwiftAidConfigFetcher").apply { isDaemon = true }
        }.also { scheduler ->
            scheduler.scheduleWithFixedDelay(
                { fetchLatestConfig() },
                initialDelaySeconds,
                periodSeconds,
                TimeUnit.SECONDS
            )
        }
    }

    fun stop() {
        running.set(false)
        executor?.shutdownNow()
        executor = null
    }

    fun fetchLatestConfig(): Boolean {
        return runCatching {
            val connection = (URL(configUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            try {
                if (connection.responseCode !in 200..299) {
                    return@runCatching false
                }

                connection.inputStream.bufferedReader().use { reader ->
                    val config = CrashThresholdConfig.fromJson(JSONObject(reader.readText()))
                    repository.saveIfNewer(config)
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Unable to fetch threshold config", throwable)
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "ConfigManager"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val DEFAULT_FETCH_PERIOD_SECONDS = 6L * 60L * 60L
    }
}
