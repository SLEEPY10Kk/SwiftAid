package com.example.swiftaid.logging

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

class CrashDataUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val file = File(filePath)
        if (!file.exists()) return Result.success()

        val uploaded = CrashDataUploader.uploadCrashFile(file)
        return if (uploaded) {
            file.delete()
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_FILE_PATH = "file_path"
    }
}
