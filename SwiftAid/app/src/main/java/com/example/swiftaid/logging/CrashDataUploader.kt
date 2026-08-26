package com.example.swiftaid.logging

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.swiftaid.BuildConfig
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object CrashDataUploader {
    fun uploadCrashData(
        snapshots: List<SensorSnapshot>,
        label: CrashDataLabel,
        serverBaseUrl: String = SERVER_BASE_URL
    ): Boolean {
        if (snapshots.isEmpty()) {
            return false
        }

        return runCatching {
            val fileName = "swift_aid_${label.csvValue}_${System.currentTimeMillis()}.csv"
            uploadCsvBytes(SensorSnapshotCsvWriter.toCsvBytes(snapshots, label), fileName, serverBaseUrl)
        }.onFailure { throwable ->
            Log.w(TAG, "Unable to upload ${label.csvValue} crash data", throwable)
        }.getOrDefault(false)
    }

    fun uploadCrashFile(
        file: File,
        serverBaseUrl: String = SERVER_BASE_URL
    ): Boolean {
        if (!file.exists() || file.length() == 0L) {
            return false
        }

        return runCatching {
            uploadCsvBytes(file.readBytes(), file.name, serverBaseUrl)
        }.onFailure { throwable ->
            Log.w(TAG, "Unable to upload crash data file ${file.name}", throwable)
        }.getOrDefault(false)
    }

    fun enqueueCrashFileUpload(context: Context, file: File) {
        if (!file.exists()) return

        val request = OneTimeWorkRequestBuilder<CrashDataUploadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(CrashDataUploadWorker.KEY_FILE_PATH, file.absolutePath)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "crash-data-upload-${file.name}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun uploadCsvBytes(
        csvBytes: ByteArray,
        fileName: String,
        serverBaseUrl: String
    ): Boolean {
        val boundary = "SwiftAid-${UUID.randomUUID()}"
        val connection = (URL("$serverBaseUrl/training/csv").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.append("--").append(boundary).append("\r\n")
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                        .append(fileName)
                        .append("\"\r\n")
                    writer.append("Content-Type: text/csv\r\n\r\n")
                    writer.flush()

                    outputStream.write(csvBytes)
                    outputStream.flush()

                    writer.append("\r\n--").append(boundary).append("--\r\n")
                }
            }

            return connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    val SERVER_BASE_URL: String
        get() = BuildConfig.KSHITI_API_BASE_URL.trimEnd('/')

    private const val TAG = "CrashDataUploader"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
}
