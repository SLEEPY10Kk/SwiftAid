package com.example.swiftaid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.example.swiftaid.emergency.EmergencyResponderMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class EmergencySmsDispatchResult(
    val attemptedContacts: Int,
    val sentParts: Int,
    val message: String
)

object EmergencySmsDispatcher {
    const val PREFS_NAME = "swift_aid_sos"
    const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"

    fun dispatch(
        context: Context,
        locationResult: SosLocationResult,
        eventId: String = UUID.randomUUID().toString(),
        relayDepth: Int = 0
    ): EmergencySmsDispatchResult {
        val message = buildMessage(
            context = context,
            locationResult = locationResult,
            eventId = eventId,
            relayDepth = relayDepth,
            userPhone = UserEmergencyProfile.load(context).phone,
            nearestResponders = emptyList()
        )
        return dispatchMessage(context, message)
    }

    fun dispatchMessage(
        context: Context,
        message: String,
        excludeNumbers: List<String> = emptyList()
    ): EmergencySmsDispatchResult {
        val excluded = excludeNumbers.map(::normalizePhoneNumber).toSet()
        val contacts = getEmergencyContacts(context)
            .filterNot { normalizePhoneNumber(it) in excluded }

        if (
            contacts.isEmpty() ||
            context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            return EmergencySmsDispatchResult(contacts.size, 0, message)
        }

        val smsManager = SmsManager.getDefault()
        var sentParts = 0
        contacts.forEach { phoneNumber ->
            runCatching {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                sentParts += parts.size
            }.onFailure { throwable ->
                Log.w(TAG, "Unable to send emergency SMS to $phoneNumber", throwable)
            }
        }

        return EmergencySmsDispatchResult(contacts.size, sentParts, message)
    }

    fun getEmergencyContacts(context: Context): List<String> {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMERGENCY_CONTACTS, "")
            .orEmpty()
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun saveEmergencyContacts(context: Context, contacts: String) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMERGENCY_CONTACTS, contacts)
            .apply()
    }

    fun buildMessage(
        context: Context,
        locationResult: SosLocationResult,
        eventId: String,
        relayDepth: Int,
        userPhone: String,
        nearestResponders: List<EmergencyResponderMatch> = emptyList(),
        responderCacheUpdatedAtMillis: Long? = null
    ): String {
        val location = locationResult.location
        val lat = location?.latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "NA"
        val lon = location?.longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "NA"
        val speed = location?.takeIf { it.hasSpeed() }?.speed ?: 0f
        val mapsLink = location?.let {
            "https://maps.google.com/?q=$lat,$lon"
        } ?: "Location unavailable"

        return buildList {
            add("SWIFTAID SOS CRASH DETECTED")
            add("EVENT:$eventId|RELAY:$relayDepth")
            userPhone.takeIf { it.isNotBlank() }?.let { add("USER_PHONE:$it") }
            add("LAT:$lat|LONG:$lon|SPEED:${String.format(Locale.US, "%.1f", speed)}")
            if (nearestResponders.isNotEmpty()) {
                add("CACHED RESPONDER INFO - MAY BE OUTDATED")
                responderCacheUpdatedAtMillis?.let {
                    add("RESPONDER_CACHE_UPDATED:${formatCacheTimestamp(it)}")
                }
                add("Verify distance and availability before relying on these contacts.")
            }
            nearestResponders.forEach { responder ->
                add(responder.toSmsLine())
            }
            add("Crash detected. Open in Google Maps: $mapsLink")
        }.joinToString(separator = "\n")
    }

    private fun EmergencyResponderMatch.toSmsLine(): String {
        val distance = if (distanceMeters >= 1_000.0) {
            String.format(Locale.US, "%.1fkm", distanceMeters / 1_000.0)
        } else {
            "${distanceMeters.toInt()}m"
        }
        return "$serviceType:$name|PHONE:$phoneNumber|DIST:$distance"
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return PhoneNumberUtils.normalizeNumber(phoneNumber).ifBlank {
            phoneNumber.filter { it.isDigit() || it == '+' }
        }
    }

    private fun formatCacheTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))
    }

    private const val TAG = "EmergencySmsDispatcher"
}
