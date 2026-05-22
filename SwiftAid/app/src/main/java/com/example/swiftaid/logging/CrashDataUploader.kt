package com.example.swiftaid.logging

import android.util.Log
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
            val boundary = "SwiftAid-${UUID.randomUUID()}"
            val fileName = "swift_aid_${label.csvValue}_${System.currentTimeMillis()}.csv"
            val connection = (URL("$serverBaseUrl/upload-crash-data").openConnection() as HttpURLConnection).apply {
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

                        outputStream.write(SensorSnapshotCsvWriter.toCsvBytes(snapshots, label))
                        outputStream.flush()

                        writer.append("\r\n--").append(boundary).append("--\r\n")
                    }
                }

                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Unable to upload ${label.csvValue} crash data", throwable)
        }.getOrDefault(false)
    }

    const val SERVER_BASE_URL = "http://192.168.29.100:5001"

    private const val TAG = "CrashDataUploader"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
}
