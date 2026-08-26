package com.example.swiftaid.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.swiftaid.EmergencySmsDispatchResult
import com.example.swiftaid.SosLocationResult
import com.example.swiftaid.UserEmergencyProfile
import com.example.swiftaid.emergency.EmergencyConnectionPacket
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

object FirebaseSosEventManager {
    private const val TAG = "FirebaseSosEventManager"

    fun createSosEvent(
        context: Context,
        locationResult: SosLocationResult,
        sosType: String,
        emergencyContacts: List<String>,
        smsResult: EmergencySmsDispatchResult,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val location = locationResult.location
        val latitude = location?.latitude ?: 0.0
        val longitude = location?.longitude ?: 0.0
        val speed = location?.takeIf { it.hasSpeed() }?.speed ?: 0f
        val mapsUrl = location?.let {
            val lat = String.format(Locale.US, "%.6f", latitude)
            val lon = String.format(Locale.US, "%.6f", longitude)
            "https://maps.google.com/?q=$lat,$lon"
        }.orEmpty()
        val profile = UserEmergencyProfile.load(context)

        val event = hashMapOf(
            "victimName" to profile.fullName.ifBlank { "SwiftAid User" },
            "victimPhone" to profile.phone,
            "latitude" to latitude,
            "longitude" to longitude,
            "lat" to latitude,
            "lng" to longitude,
            "address" to locationResult.source,
            "locationSource" to locationResult.source,
            "speed" to speed.toDouble(),
            "sosType" to sosType,
            "severity" to severityFor(speed, location != null),
            "description" to if (sosType == "MANUAL") "Manual SOS triggered from SwiftAid" else "Crash SOS triggered from SwiftAid",
            "status" to "ACTIVE",
            "isOnline" to true,
            "emergencyContactsNotified" to emergencyContacts,
            "emergencyContactCount" to emergencyContacts.size,
            "smsPartsSent" to smsResult.sentParts,
            "smsMessage" to smsResult.message,
            "mapsUrl" to mapsUrl,
            "sourceApp" to "SwiftAid",
            "deviceModel" to Build.MODEL.orEmpty(),
            "androidVersion" to Build.VERSION.RELEASE.orEmpty(),
            "swiftAidVersion" to appVersion(context),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("sos_events")
            .add(event)
            .addOnSuccessListener { document ->
                Log.d(TAG, "SOS event created in Firebase: ${document.id}")
                onComplete(true)
            }
            .addOnFailureListener { throwable ->
                Log.e(TAG, "Failed to create SOS event in Firebase", throwable)
                onComplete(false)
            }
    }

    fun uploadEmergencyPacket(
        context: Context,
        packet: EmergencyConnectionPacket,
        communicationLevel: String,
        deliveryState: String,
        smsResult: EmergencySmsDispatchResult?,
        onComplete: (Boolean) -> Unit
    ) {
        val latitude = packet.latitude ?: 0.0
        val longitude = packet.longitude ?: 0.0
        val speed = packet.speedMetersPerSecond ?: 0.0
        val event = hashMapOf<String, Any?>(
            "eventId" to packet.eventId,
            "victimName" to packet.userName.ifBlank { "SwiftAid User" },
            "victimPhone" to packet.userPhone,
            "latitude" to latitude,
            "longitude" to longitude,
            "lat" to latitude,
            "lng" to longitude,
            "address" to packet.locationSource,
            "locationSource" to packet.locationSource,
            "speed" to speed,
            "sosType" to packet.sosType,
            "severity" to severityFor(speed.toFloat(), packet.hasCoordinates),
            "description" to descriptionFor(packet.sosType, communicationLevel),
            "status" to "ACTIVE",
            "isOnline" to true,
            "emergencyContactsNotified" to packet.emergencyContacts,
            "emergencyContactCount" to packet.emergencyContacts.size,
            "nearestResponders" to emptyList<Map<String, Any>>(),
            "targetResponderIds" to emptyList<String>(),
            "targetServiceTypes" to emptyList<String>(),
            "nearestHospitalId" to "",
            "nearestHospitalName" to "",
            "nearestHospitalPhone" to "",
            "nearestHospitalDistanceMeters" to 0.0,
            "nearestHospitalRouteUrl" to "",
            "nearestPoliceId" to "",
            "nearestPoliceName" to "",
            "nearestPolicePhone" to "",
            "nearestPoliceDistanceMeters" to 0.0,
            "nearestPoliceRouteUrl" to "",
            "cachedSmsResponders" to packet.nearestResponders.map { it.toFirestoreMap() },
            "responderCacheUpdatedAtMillis" to packet.responderCacheUpdatedAtMillis,
            "smsPartsSent" to (smsResult?.sentParts ?: 0),
            "smsMessage" to packet.smsMessage,
            "mapsUrl" to packet.mapsUrl,
            "sourceApp" to "SwiftAid",
            "source" to packet.source,
            "communicationLevel" to communicationLevel,
            "deliveryState" to deliveryState,
            "relayDepth" to packet.relayDepth,
            "relaySender" to packet.sender.orEmpty(),
            "createdAtClientMillis" to packet.createdAtMillis,
            "deviceModel" to Build.MODEL.orEmpty(),
            "androidVersion" to Build.VERSION.RELEASE.orEmpty(),
            "swiftAidVersion" to appVersion(context),
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("sos_events")
            .document(packet.eventId)
            .set(event, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "SOS event uploaded to Firebase: ${packet.eventId}")
                onComplete(true)
            }
            .addOnFailureListener { throwable ->
                Log.e(TAG, "Failed to upload SOS event to Firebase", throwable)
                onComplete(false)
            }
    }

    private fun severityFor(speed: Float, hasLocation: Boolean): String {
        if (!hasLocation) return "HIGH"
        return when {
            speed >= 30f -> "CRITICAL"
            speed >= 15f -> "HIGH"
            else -> "MEDIUM"
        }
    }

    private fun descriptionFor(sosType: String, communicationLevel: String): String {
        val trigger = if (sosType == "MANUAL") "Manual SOS" else "Crash SOS"
        return "$trigger triggered from SwiftAid through $communicationLevel"
    }

    private fun appVersion(context: Context): String {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: ""
        }.getOrDefault("")
    }
}
