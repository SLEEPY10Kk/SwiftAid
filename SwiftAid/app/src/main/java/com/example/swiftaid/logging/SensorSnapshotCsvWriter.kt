package com.example.swiftaid.logging

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SensorSnapshotCsvWriter {
    private val writeLock = Any()

    fun writeCrashLog(
        context: Context,
        snapshots: List<SensorSnapshot>,
        label: CrashDataLabel
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "crash_log_${label.csvValue}_$timestamp.csv"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logDir = File(downloadsDir, LOG_FOLDER)
        val file = File(logDir, fileName)

        synchronized(writeLock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeWithMediaStore(context, fileName, snapshots, label)
            } else {
                if (!logDir.exists() && !logDir.mkdirs()) {
                    error("Unable to create ${logDir.absolutePath}")
                }
                file.bufferedWriter().use { writer ->
                    writeRows(writer, snapshots, label)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("text/csv"),
                    null
                )
            }
        }

        return file
    }

    private fun writeWithMediaStore(
        context: Context,
        fileName: String,
        snapshots: List<SensorSnapshot>,
        label: CrashDataLabel
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$LOG_FOLDER"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create crash log in Downloads")

        runCatching {
            resolver.openOutputStream(uri, "w")?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writeRows(writer, snapshots, label)
                }
            } ?: error("Unable to open crash log output stream")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure { throwable ->
            resolver.delete(uri, null, null)
            throw throwable
        }.getOrThrow()
    }

    private fun writeRows(
        writer: Appendable,
        snapshots: List<SensorSnapshot>,
        label: CrashDataLabel
    ) {
        writer.appendLine("Timestamp,AccelX,AccelY,AccelZ,GyroX,GyroY,GyroZ,Lat,Lon,Label")
        snapshots.forEach { snapshot ->
            writer.append(snapshot.timestamp.toString())
            writer.append(',')
            writer.append(snapshot.accelX.csvFloat())
            writer.append(',')
            writer.append(snapshot.accelY.csvFloat())
            writer.append(',')
            writer.append(snapshot.accelZ.csvFloat())
            writer.append(',')
            writer.append(snapshot.gyroX.csvFloat())
            writer.append(',')
            writer.append(snapshot.gyroY.csvFloat())
            writer.append(',')
            writer.append(snapshot.gyroZ.csvFloat())
            writer.append(',')
            writer.append(snapshot.lat?.csvDouble().orEmpty())
            writer.append(',')
            writer.append(snapshot.lon?.csvDouble().orEmpty())
            writer.append(',')
            writer.append(snapshot.label ?: label.csvValue)
            writer.appendLine()
        }
    }

    private fun Float.csvFloat(): String = String.format(Locale.US, "%.6f", this)

    private fun Double.csvDouble(): String = String.format(Locale.US, "%.8f", this)

    private const val LOG_FOLDER = "SwiftAidLogs"
}
