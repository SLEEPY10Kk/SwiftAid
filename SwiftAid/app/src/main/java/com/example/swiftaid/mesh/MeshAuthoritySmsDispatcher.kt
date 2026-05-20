package com.example.swiftaid.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeshAuthoritySmsResult(
    val attemptedContacts: Int,
    val sentParts: Int,
    val message: String
)

object MeshAuthoritySmsDispatcher {
    const val PREFS_NAME = "swift_aid_mesh"
    const val KEY_AUTHORITY_CONTACTS = "authority_contacts"

    fun dispatch(context: Context, packet: MeshSosPacket): MeshAuthoritySmsResult {
        val message = buildMessage(packet)
        val contacts = getAuthorityContacts(context)
        if (
            contacts.isEmpty() ||
            context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            return MeshAuthoritySmsResult(contacts.size, 0, message)
        }

        val smsManager = SmsManager.getDefault()
        var sentParts = 0
        contacts.forEach { phoneNumber ->
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            sentParts += parts.size
        }

        return MeshAuthoritySmsResult(contacts.size, sentParts, message)
    }

    fun getAuthorityContacts(context: Context): List<String> {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTHORITY_CONTACTS, "")
            .orEmpty()
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun saveAuthorityContacts(context: Context, contacts: String) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTHORITY_CONTACTS, contacts)
            .apply()
    }

    private fun buildMessage(packet: MeshSosPacket): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date(packet.timestampMs))
        val locationLine = if (packet.latitude != null && packet.longitude != null) {
            val lat = String.format(Locale.US, "%.6f", packet.latitude)
            val lng = String.format(Locale.US, "%.6f", packet.longitude)
            "Google Maps: https://maps.google.com/?q=$lat,$lng\nCoordinates: $lat, $lng"
        } else {
            "Location: unavailable (${packet.locationSource})"
        }

        val accuracyLine = packet.accuracyMeters?.let {
            "\nAccuracy: +/-${String.format(Locale.US, "%.1f", it)}m"
        }.orEmpty()

        return """
            SwiftAid mesh SOS received.
            $locationLine$accuracyLine
            Source: ${packet.locationSource}
            Packet: ${packet.packetId}
            Remaining hops: ${packet.ttl}
            Timestamp: $timestamp
        """.trimIndent()
    }
}
