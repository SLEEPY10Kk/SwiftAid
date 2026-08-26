package com.example.swiftaid.emergency

import com.example.swiftaid.EmergencySmsDispatcher
import com.example.swiftaid.SosLocationResult
import com.example.swiftaid.offline.OfflineSosPayload
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class EmergencyConnectionPacket(
    val eventId: String,
    val sosType: String,
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Double?,
    val locationSource: String,
    val userName: String,
    val userPhone: String,
    val emergencyContacts: List<String>,
    val nearestResponders: List<EmergencyResponderMatch>,
    val responderCacheUpdatedAtMillis: Long?,
    val smsMessage: String,
    val relayDepth: Int,
    val sender: String?,
    val createdAtMillis: Long,
    val source: String
) {
    val hasCoordinates: Boolean = latitude != null && longitude != null

    val mapsUrl: String
        get() = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=${formatCoordinate(latitude)},${formatCoordinate(longitude)}"
        } else {
            ""
        }

    fun relayMessage(nextRelayDepth: Int): String {
        return buildString {
            append(smsMessage.trim())
            append("\nRELAY:")
            append(nextRelayDepth)
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put(KEY_EVENT_ID, eventId)
            put(KEY_SOS_TYPE, sosType)
            latitude?.let { put(KEY_LATITUDE, it) }
            longitude?.let { put(KEY_LONGITUDE, it) }
            speedMetersPerSecond?.let { put(KEY_SPEED, it) }
            put(KEY_LOCATION_SOURCE, locationSource)
            put(KEY_USER_NAME, userName)
            put(KEY_USER_PHONE, userPhone)
            put(KEY_EMERGENCY_CONTACTS, emergencyContacts.toJsonArray())
            put(KEY_NEAREST_RESPONDERS, nearestResponders.toResponderJsonArray())
            responderCacheUpdatedAtMillis?.let { put(KEY_RESPONDER_CACHE_UPDATED_AT_MILLIS, it) }
            put(KEY_SMS_MESSAGE, smsMessage)
            put(KEY_RELAY_DEPTH, relayDepth)
            sender?.let { put(KEY_SENDER, it) }
            put(KEY_CREATED_AT_MILLIS, createdAtMillis)
            put(KEY_SOURCE, source)
        }
    }

    companion object {
        private const val KEY_EVENT_ID = "eventId"
        private const val KEY_SOS_TYPE = "sosType"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_SPEED = "speedMetersPerSecond"
        private const val KEY_LOCATION_SOURCE = "locationSource"
        private const val KEY_USER_NAME = "userName"
        private const val KEY_USER_PHONE = "userPhone"
        private const val KEY_EMERGENCY_CONTACTS = "emergencyContacts"
        private const val KEY_NEAREST_RESPONDERS = "nearestResponders"
        private const val KEY_RESPONDER_CACHE_UPDATED_AT_MILLIS = "responderCacheUpdatedAtMillis"
        private const val KEY_SMS_MESSAGE = "smsMessage"
        private const val KEY_RELAY_DEPTH = "relayDepth"
        private const val KEY_SENDER = "sender"
        private const val KEY_CREATED_AT_MILLIS = "createdAtMillis"
        private const val KEY_SOURCE = "source"

        const val SOURCE_DEVICE = "DEVICE"
        const val SOURCE_SMS_RELAY = "SMS_RELAY"

        fun fromLocation(
            context: android.content.Context,
            locationResult: SosLocationResult,
            sosType: String,
            emergencyContacts: List<String>,
            nearestResponders: List<EmergencyResponderMatch> = emptyList(),
            responderCacheUpdatedAtMillis: Long? = null,
            eventId: String = UUID.randomUUID().toString(),
            createdAtMillis: Long = System.currentTimeMillis()
        ): EmergencyConnectionPacket {
            val location = locationResult.location
            val speed = location?.takeIf { it.hasSpeed() }?.speed?.toDouble()
            val profile = com.example.swiftaid.UserEmergencyProfile.load(context)
            val smsMessage = EmergencySmsDispatcher.buildMessage(
                context = context,
                locationResult = locationResult,
                eventId = eventId,
                relayDepth = 0,
                userPhone = profile.phone,
                nearestResponders = nearestResponders,
                responderCacheUpdatedAtMillis = responderCacheUpdatedAtMillis
            )

            return EmergencyConnectionPacket(
                eventId = eventId,
                sosType = sosType,
                latitude = location?.latitude,
                longitude = location?.longitude,
                speedMetersPerSecond = speed,
                locationSource = locationResult.source,
                userName = profile.fullName,
                userPhone = profile.phone,
                emergencyContacts = emergencyContacts,
                nearestResponders = nearestResponders,
                responderCacheUpdatedAtMillis = responderCacheUpdatedAtMillis,
                smsMessage = smsMessage,
                relayDepth = 0,
                sender = null,
                createdAtMillis = createdAtMillis,
                source = SOURCE_DEVICE
            )
        }

        fun fromRelayPayload(
            payload: OfflineSosPayload,
            createdAtMillis: Long = System.currentTimeMillis()
        ): EmergencyConnectionPacket {
            val eventId = payload.eventId ?: stableRelayEventId(payload)
            return EmergencyConnectionPacket(
                eventId = eventId,
                sosType = "CRASH",
                latitude = payload.latitude,
                longitude = payload.longitude,
                speedMetersPerSecond = payload.speedMetersPerSecond,
                locationSource = "SMS relay from ${payload.sender.orEmpty().ifBlank { "unknown sender" }}",
                userName = "",
                userPhone = payload.userPhone.orEmpty(),
                emergencyContacts = emptyList(),
                nearestResponders = emptyList(),
                responderCacheUpdatedAtMillis = null,
                smsMessage = payload.rawMessage,
                relayDepth = payload.relayDepth,
                sender = payload.sender,
                createdAtMillis = createdAtMillis,
                source = SOURCE_SMS_RELAY
            )
        }

        fun fromJson(json: JSONObject): EmergencyConnectionPacket {
            return EmergencyConnectionPacket(
                eventId = json.getString(KEY_EVENT_ID),
                sosType = json.optString(KEY_SOS_TYPE, "CRASH"),
                latitude = json.optNullableDouble(KEY_LATITUDE),
                longitude = json.optNullableDouble(KEY_LONGITUDE),
                speedMetersPerSecond = json.optNullableDouble(KEY_SPEED),
                locationSource = json.optString(KEY_LOCATION_SOURCE, "Unknown"),
                userName = json.optString(KEY_USER_NAME),
                userPhone = json.optString(KEY_USER_PHONE),
                emergencyContacts = json.optJSONArray(KEY_EMERGENCY_CONTACTS).toStringList(),
                nearestResponders = json.optJSONArray(KEY_NEAREST_RESPONDERS).toResponderList(),
                responderCacheUpdatedAtMillis = json.optNullableLong(KEY_RESPONDER_CACHE_UPDATED_AT_MILLIS),
                smsMessage = json.optString(KEY_SMS_MESSAGE),
                relayDepth = json.optInt(KEY_RELAY_DEPTH, 0),
                sender = json.optNullableString(KEY_SENDER),
                createdAtMillis = json.optLong(KEY_CREATED_AT_MILLIS, System.currentTimeMillis()),
                source = json.optString(KEY_SOURCE, SOURCE_DEVICE)
            )
        }

        private fun stableRelayEventId(payload: OfflineSosPayload): String {
            val input = "${payload.sender.orEmpty()}|${payload.rawMessage}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray())
                .joinToString(separator = "") { "%02x".format(it) }
                .take(24)
            return "relay-$digest"
        }

        private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
    }
}

private fun List<String>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it) }
    return array
}

private fun List<EmergencyResponderMatch>.toResponderJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { responder ->
        array.put(
            JSONObject().apply {
                put("id", responder.id)
                put("serviceType", responder.serviceType)
                put("name", responder.name)
                put("phoneNumber", responder.phoneNumber)
                put("latitude", responder.latitude)
                put("longitude", responder.longitude)
                put("distanceMeters", responder.distanceMeters)
                put("routeUrl", responder.routeUrl)
            }
        )
    }
    return array
}

private fun JSONArray?.toStringList(): List<String> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun JSONArray?.toResponderList(): List<EmergencyResponderMatch> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val serviceType = item.optString("serviceType")
            val name = item.optString("name")
            val phone = item.optString("phoneNumber")
            val routeUrl = item.optString("routeUrl")
            if (id.isBlank() || serviceType.isBlank() || name.isBlank() || phone.isBlank()) continue
            add(
                EmergencyResponderMatch(
                    id = id,
                    serviceType = serviceType,
                    name = name,
                    phoneNumber = phone,
                    latitude = item.optDouble("latitude"),
                    longitude = item.optDouble("longitude"),
                    distanceMeters = item.optDouble("distanceMeters"),
                    routeUrl = routeUrl
                )
            )
        }
    }
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    return if (has(key) && !isNull(key)) optDouble(key) else null
}

private fun JSONObject.optNullableString(key: String): String? {
    return if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}

private fun JSONObject.optNullableLong(key: String): Long? {
    return if (has(key) && !isNull(key)) optLong(key) else null
}
