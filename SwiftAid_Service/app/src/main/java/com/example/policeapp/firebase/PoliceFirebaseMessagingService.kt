package com.example.policeapp.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.policeapp.MainActivity
import com.example.policeapp.R
import com.example.policeapp.data.ResponderSession
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firebase Cloud Messaging service for PoliceApp/HospitalApp
 * Receives SOS alerts and status updates
 */
class PoliceFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "SOS notification received from: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")
        
        val data = remoteMessage.data
        
        // Handle different message types
        when (data["type"]) {
            "sos_alert" -> handleSosAlert(data)
            "sos_dismiss" -> handleSosDismiss(data)
            "sos_assigned" -> handleSosAssigned(data)
            "sos_completed" -> handleSosCompleted(data)
            else -> Log.d(TAG, "Unknown message type: ${data["type"]}")
        }
    }
    
    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
        saveFcmTokenToFirebase(token)
    }
    
    /**
     * Handle incoming SOS alert
     * This is the main alert when a new SOS event is created
     */
    private fun handleSosAlert(data: Map<String, String>) {
        val sosId = data["sos_id"] ?: return
        val victimName = data["victim_name"] ?: "Unknown Victim"
        val distance = data["distance_meters"]?.toIntOrNull() ?: 0
        val severity = data["severity"] ?: "HIGH"
        val address = data["address"] ?: "Unknown Location"
        
        Log.d(TAG, "SOS ALERT: $sosId - $victimName ($distance m away) - Severity: $severity")
        
        createNotificationChannel()
        
        // Create notification with high priority and sound
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("sos_id", sosId)
            .putExtra("navigate_to_sos", true)
            .putExtra("latitude", data["lat"]?.toDoubleOrNull() ?: 0.0)
            .putExtra("longitude", data["lng"]?.toDoubleOrNull() ?: 0.0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            sosId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val severityEmoji = when (severity) {
            "CRITICAL" -> "🚨"
            "HIGH" -> "⚠️"
            "MEDIUM" -> "⚡"
            else -> "📍"
        }
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_SOS_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$severityEmoji NEW SOS ALERT - $severity")
            .setContentText("$victimName • ${distance}m away • $address")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Patient: $victimName\nLocation: $address\nDistance: ${distance}m\nSeverity: $severity")
            )
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setLights(0xFF0000, 1000, 1000) // Red blinking light
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Accept",
                pendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Dismiss",
                getPendingIntentForAction("dismiss", sosId)
            )
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(sosId.hashCode(), notificationBuilder.build())
    }
    
    /**
     * Handle SOS dismissal
     * Called when another responder accepted this SOS
     */
    private fun handleSosDismiss(data: Map<String, String>) {
        val sosId = data["sos_id"] ?: return
        Log.d(TAG, "SOS dismissed: $sosId")
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(sosId.hashCode())
    }
    
    /**
     * Handle SOS assignment confirmation
     */
    private fun handleSosAssigned(data: Map<String, String>) {
        val sosId = data["sos_id"] ?: return
        Log.d(TAG, "SOS assigned to you: $sosId")
        
        createNotificationChannel()
        
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("sos_id", sosId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            sosId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_SOS_UPDATE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("✅ Assignment Confirmed")
            .setContentText("You have been assigned to SOS #${sosId.substring(0, 8)}")
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(sosId.hashCode() + 1, notificationBuilder.build())
    }
    
    /**
     * Handle SOS completion notification
     */
    private fun handleSosCompleted(data: Map<String, String>) {
        val sosId = data["sos_id"] ?: return
        Log.d(TAG, "SOS completed: $sosId")
        
        createNotificationChannel()
        
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("sos_id", sosId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            sosId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_SOS_UPDATE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎉 SOS Completed")
            .setContentText("SOS #${sosId.substring(0, 8)} has been completed")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(sosId.hashCode() + 2, notificationBuilder.build())
    }
    
    /**
     * Create notification channels for different notification types
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // High priority SOS alerts channel
            val sosAlertChannel = NotificationChannel(
                CHANNEL_SOS_ALERT,
                "SOS Emergency Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Critical SOS emergency alerts"
                enableVibration(true)
                enableLights(true)
                lightColor = 0xFFFF0000.toInt()
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setBypassDnd(true)
            }
            
            // SOS update channel
            val sosUpdateChannel = NotificationChannel(
                CHANNEL_SOS_UPDATE,
                "SOS Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Updates about SOS events"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            
            notificationManager.createNotificationChannel(sosAlertChannel)
            notificationManager.createNotificationChannel(sosUpdateChannel)
        }
    }
    
    /**
     * Create pending intent for notification actions
     */
    private fun getPendingIntentForAction(action: String, sosId: String): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java)
            .setAction("com.example.policeapp.action.$action")
            .putExtra("sos_id", sosId)
        
        return PendingIntent.getBroadcast(
            this,
            (sosId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Save FCM token to Firestore
     */
    private fun saveFcmTokenToFirebase(token: String) {
        Log.d(TAG, "Saving FCM token: $token")
        val responder = ResponderSession.load(this) ?: return
        FirebaseFirestore.getInstance()
            .collection("responders")
            .document(responder.id)
            .update("fcmToken", token)
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to update responder FCM token", error)
            }
    }
    
    companion object {
        private const val TAG = "PoliceMessagingService"
        private const val CHANNEL_SOS_ALERT = "sos_emergency_alerts"
        private const val CHANNEL_SOS_UPDATE = "sos_updates"
    }
}

/**
 * Broadcast receiver for notification actions
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val sosId = intent.getStringExtra("sos_id") ?: return
        
        Log.d("NotificationAction", "Action: $action for SOS: $sosId")
        
        when {
            action.endsWith("dismiss") -> {
                Log.d("NotificationAction", "Dismissing notification for $sosId")
                // Handle dismiss action
            }
            else -> Log.d("NotificationAction", "Unknown action: $action")
        }
    }
}
