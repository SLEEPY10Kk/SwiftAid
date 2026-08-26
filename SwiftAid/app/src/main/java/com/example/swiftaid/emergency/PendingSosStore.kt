package com.example.swiftaid.emergency

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class PendingSosRecord(
    val packet: EmergencyConnectionPacket,
    val reason: String,
    val attempts: Int,
    val firstQueuedAtMillis: Long,
    val lastAttemptAtMillis: Long?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put(KEY_PACKET, packet.toJson())
            put(KEY_REASON, reason)
            put(KEY_ATTEMPTS, attempts)
            put(KEY_FIRST_QUEUED_AT_MILLIS, firstQueuedAtMillis)
            lastAttemptAtMillis?.let { put(KEY_LAST_ATTEMPT_AT_MILLIS, it) }
        }
    }

    companion object {
        private const val KEY_PACKET = "packet"
        private const val KEY_REASON = "reason"
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_FIRST_QUEUED_AT_MILLIS = "firstQueuedAtMillis"
        private const val KEY_LAST_ATTEMPT_AT_MILLIS = "lastAttemptAtMillis"

        fun fromJson(json: JSONObject): PendingSosRecord {
            return PendingSosRecord(
                packet = EmergencyConnectionPacket.fromJson(json.getJSONObject(KEY_PACKET)),
                reason = json.optString(KEY_REASON, "unknown"),
                attempts = json.optInt(KEY_ATTEMPTS, 0),
                firstQueuedAtMillis = json.optLong(KEY_FIRST_QUEUED_AT_MILLIS, System.currentTimeMillis()),
                lastAttemptAtMillis = if (json.has(KEY_LAST_ATTEMPT_AT_MILLIS)) {
                    json.optLong(KEY_LAST_ATTEMPT_AT_MILLIS)
                } else {
                    null
                }
            )
        }
    }
}

object PendingSosStore {
    private const val TAG = "PendingSosStore"
    private const val PREFS_NAME = "swift_aid_pending_sos"
    private const val KEY_PENDING_RECORDS = "pending_records"

    @Synchronized
    fun enqueue(context: Context, packet: EmergencyConnectionPacket, reason: String) {
        val now = System.currentTimeMillis()
        val records = snapshot(context).toMutableList()
        val existingIndex = records.indexOfFirst { it.packet.eventId == packet.eventId }
        val record = if (existingIndex >= 0) {
            records[existingIndex].copy(packet = packet, reason = reason)
        } else {
            PendingSosRecord(
                packet = packet,
                reason = reason,
                attempts = 0,
                firstQueuedAtMillis = now,
                lastAttemptAtMillis = null
            )
        }

        if (existingIndex >= 0) {
            records[existingIndex] = record
        } else {
            records.add(record)
        }
        write(context, records)
        Log.i(TAG, "Queued pending SOS ${packet.eventId}: $reason")
    }

    @Synchronized
    fun snapshot(context: Context): List<PendingSosRecord> {
        val raw = prefs(context).getString(KEY_PENDING_RECORDS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(PendingSosRecord.fromJson(array.getJSONObject(index)))
                }
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Unable to read pending SOS store", throwable)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun markAttempt(context: Context, eventId: String) {
        val records = snapshot(context).map { record ->
            if (record.packet.eventId == eventId) {
                record.copy(
                    attempts = record.attempts + 1,
                    lastAttemptAtMillis = System.currentTimeMillis()
                )
            } else {
                record
            }
        }
        write(context, records)
    }

    @Synchronized
    fun remove(context: Context, eventId: String) {
        write(context, snapshot(context).filterNot { it.packet.eventId == eventId })
    }

    private fun write(context: Context, records: List<PendingSosRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs(context)
            .edit()
            .putString(KEY_PENDING_RECORDS, array.toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
