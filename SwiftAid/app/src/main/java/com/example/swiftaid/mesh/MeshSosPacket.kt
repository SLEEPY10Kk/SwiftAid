package com.example.swiftaid.mesh

import android.content.Context
import com.example.swiftaid.SosLocationResult
import org.json.JSONObject
import java.util.UUID

data class MeshSosPacket(
    val packetId: String,
    val originDeviceId: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val locationSource: String,
    val timestampMs: Long,
    val ttl: Int
) {
    fun canRelay(): Boolean = ttl > 0

    fun relayed(): MeshSosPacket = copy(ttl = (ttl - 1).coerceAtLeast(0))

    fun toJson(): String {
        return JSONObject()
            .put(KEY_TYPE, TYPE_SOS)
            .put(KEY_PACKET_ID, packetId)
            .put(KEY_ORIGIN_DEVICE_ID, originDeviceId)
            .put(KEY_TIMESTAMP_MS, timestampMs)
            .put(KEY_TTL, ttl)
            .put(KEY_LOCATION_SOURCE, locationSource)
            .apply {
                latitude?.let { put(KEY_LATITUDE, it) }
                longitude?.let { put(KEY_LONGITUDE, it) }
                accuracyMeters?.let { put(KEY_ACCURACY_METERS, it.toDouble()) }
            }
            .toString()
    }

    companion object {
        const val DEFAULT_TTL = 5
        private const val TYPE_SOS = "swift_aid_sos"
        private const val KEY_TYPE = "type"
        private const val KEY_PACKET_ID = "packet_id"
        private const val KEY_ORIGIN_DEVICE_ID = "origin_device_id"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_ACCURACY_METERS = "accuracy_m"
        private const val KEY_LOCATION_SOURCE = "location_source"
        private const val KEY_TIMESTAMP_MS = "timestamp_ms"
        private const val KEY_TTL = "ttl"

        fun fromLocationResult(context: Context, locationResult: SosLocationResult): MeshSosPacket {
            val location = locationResult.location
            return MeshSosPacket(
                packetId = UUID.randomUUID().toString(),
                originDeviceId = DeviceIdentity.get(context),
                latitude = location?.latitude,
                longitude = location?.longitude,
                accuracyMeters = location?.accuracy,
                locationSource = locationResult.source,
                timestampMs = System.currentTimeMillis(),
                ttl = DEFAULT_TTL
            )
        }

        fun fromJson(rawJson: String): MeshSosPacket? {
            return runCatching {
                val json = JSONObject(rawJson)
                if (json.optString(KEY_TYPE) != TYPE_SOS) return null
                MeshSosPacket(
                    packetId = json.getString(KEY_PACKET_ID),
                    originDeviceId = json.getString(KEY_ORIGIN_DEVICE_ID),
                    latitude = json.optNullableDouble(KEY_LATITUDE),
                    longitude = json.optNullableDouble(KEY_LONGITUDE),
                    accuracyMeters = json.optNullableDouble(KEY_ACCURACY_METERS)?.toFloat(),
                    locationSource = json.optString(KEY_LOCATION_SOURCE, "mesh"),
                    timestampMs = json.optLong(KEY_TIMESTAMP_MS, System.currentTimeMillis()),
                    ttl = json.optInt(KEY_TTL, 0).coerceIn(0, DEFAULT_TTL)
                )
            }.getOrNull()
        }

        private fun JSONObject.optNullableDouble(key: String): Double? {
            return if (has(key) && !isNull(key)) optDouble(key) else null
        }
    }
}

private object DeviceIdentity {
    private const val PREFS_NAME = "swift_aid_mesh"
    private const val KEY_DEVICE_ID = "mesh_device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }
}
