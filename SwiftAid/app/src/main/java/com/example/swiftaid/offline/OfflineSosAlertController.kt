package com.example.swiftaid.offline

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.swiftaid.R

object OfflineSosAlertController {
    const val ACTION_DISMISS = "com.example.swiftaid.action.DISMISS_OFFLINE_SOS"
    const val EXTRA_LAT = "lat"
    const val EXTRA_LNG = "lng"
    const val EXTRA_SPEED = "speed"
    const val EXTRA_SENDER = "sender"
    const val EXTRA_RAW_MESSAGE = "raw_message"
    const val EXTRA_HOSPITAL_NAME = "hospital_name"
    const val EXTRA_HOSPITAL_PHONE = "hospital_phone"
    const val EXTRA_POLICE_NAME = "police_name"
    const val EXTRA_POLICE_PHONE = "police_phone"

    private const val CHANNEL_ID = "swift_aid_offline_sms_alert"
    private const val CHANNEL_NAME = "Offline SMS SOS alerts"
    private const val NOTIFICATION_ID = 5001
    private const val FULL_SCREEN_REQUEST_CODE = 5002
    private const val WAKELOCK_TIMEOUT_MS = 60_000L
    private const val TAG = "OfflineSosAlert"

    fun showAlert(context: Context, payload: OfflineSosPayload) {
        createChannel(context)
        acquireWakeLock(context)
        OfflineSosAlarmPlayer.start(context)

        val alertIntent = Intent(context, OfflineSosAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SENDER, payload.sender)
            .putExtra(EXTRA_RAW_MESSAGE, payload.rawMessage)
            .putExtra(EXTRA_HOSPITAL_NAME, payload.hospitalName)
            .putExtra(EXTRA_HOSPITAL_PHONE, payload.hospitalPhone)
            .putExtra(EXTRA_POLICE_NAME, payload.policeName)
            .putExtra(EXTRA_POLICE_PHONE, payload.policePhone)
        payload.latitude?.let { alertIntent.putExtra(EXTRA_LAT, it) }
        payload.longitude?.let { alertIntent.putExtra(EXTRA_LNG, it) }
        payload.speedMetersPerSecond?.let { alertIntent.putExtra(EXTRA_SPEED, it) }

        val pendingIntent = PendingIntent.getActivity(
            context,
            FULL_SCREEN_REQUEST_CODE,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_swiftaid)
            .setContentTitle("SwiftAid offline SOS")
            .setContentText("Crash alert received by SMS. Tap to call local help.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .apply {
                payload.hospitalPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                    addAction(
                        R.drawable.ic_stat_swiftaid,
                        "Call hospital",
                        dialPendingIntent(context, phone, requestCode = 5101)
                    )
                }
                payload.policePhone?.takeIf { it.isNotBlank() }?.let { phone ->
                    addAction(
                        R.drawable.ic_stat_swiftaid,
                        "Call police",
                        dialPendingIntent(context, phone, requestCode = 5102)
                    )
                }
            }
            .build()

        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }.onFailure { throwable ->
            Log.w(TAG, "Could not post offline SOS notification", throwable)
        }
        runCatching { context.startActivity(alertIntent) }
            .onFailure { throwable -> Log.w(TAG, "Could not launch offline SOS activity", throwable) }
    }

    private fun dialPendingIntent(context: Context, phone: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun dismiss(context: Context) {
        OfflineSosAlarmPlayer.stop()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        context.sendBroadcast(Intent(ACTION_DISMISS).setPackage(context.packageName))
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen alerts for offline SMS crash messages"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true)
            enableVibration(true)
            setSound(
                Uri.parse("android.resource://${context.packageName}/${R.raw.offline_sos_alarm}"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(context: Context) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SwiftAid:OfflineSmsSosWakeLock"
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
    }
}
