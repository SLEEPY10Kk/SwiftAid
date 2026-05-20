package com.example.swiftaid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EmergencySmsDispatchResult(
    val attemptedContacts: Int,
    val sentParts: Int,
    val message: String
)

object EmergencySmsDispatcher {
    const val PREFS_NAME = "swift_aid_sos"
    const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"

    fun dispatch(context: Context, locationResult: SosLocationResult): EmergencySmsDispatchResult {
        val message = buildMessage(locationResult)
        val contacts = getEmergencyContacts(context)
        if (contacts.isEmpty() || context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return EmergencySmsDispatchResult(contacts.size, 0, message)
        }

        val smsManager = SmsManager.getDefault()
        var sentParts = 0
        contacts.forEach { phoneNumber ->
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            sentParts += parts.size
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

    private fun buildMessage(locationResult: SosLocationResult): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val location = locationResult.location

        return if (location != null) {
            """
            SwiftAid SOS: crash confirmed.
            Google Maps: ${mapsLink(location)}
            Coordinates: ${String.format(Locale.US, "%.6f", location.latitude)}, ${String.format(Locale.US, "%.6f", location.longitude)}
            Accuracy: +/-${String.format(Locale.US, "%.1f", location.accuracy)}m
            Source: ${locationResult.source}
            Timestamp: $timestamp
            """.trimIndent()
        } else {
            """
            SwiftAid SOS: crash confirmed.
            Location: unavailable (${locationResult.source})
            Timestamp: $timestamp
            """.trimIndent()
        }
    }

    private fun mapsLink(location: Location): String {
        return "https://maps.google.com/?q=${String.format(Locale.US, "%.6f", location.latitude)},${String.format(Locale.US, "%.6f", location.longitude)}"
    }
}
