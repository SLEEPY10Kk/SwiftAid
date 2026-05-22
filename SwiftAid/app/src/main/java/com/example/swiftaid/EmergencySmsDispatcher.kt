package com.example.swiftaid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
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
        val location = locationResult.location
        val lat = location?.latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "NA"
        val lon = location?.longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "NA"
        val speed = location?.takeIf { it.hasSpeed() }?.speed ?: 0f
        val mapsLink = location?.let {
            "https://maps.google.com/?q=$lat,$lon"
        } ?: "Location unavailable"

        return "SWIFTAID SOS CRASH DETECTED\n" +
            "LAT:$lat|LONG:$lon|SPEED:${String.format(Locale.US, "%.1f", speed)}\n" +
            "Crash detected. Open in Google Maps: $mapsLink"
    }
}
