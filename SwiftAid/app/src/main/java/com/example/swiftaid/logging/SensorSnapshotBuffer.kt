package com.example.swiftaid.logging

import android.content.Context
import java.io.File

class SensorSnapshotBuffer(
    private val maxWindowNs: Long = DEFAULT_WINDOW_NS,
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY
) {
    private val lock = Any()
    private val snapshots = ArrayList<SensorSnapshot>(initialCapacity.coerceAtLeast(1))

    fun add(snapshot: SensorSnapshot) {
        synchronized(lock) {
            snapshots += snapshot
            trimOlderThan(snapshot.timestamp - maxWindowNs)
        }
    }

    fun snapshot(): List<SensorSnapshot> {
        synchronized(lock) {
            return snapshots.toList()
        }
    }

    fun snapshotAndClear(): List<SensorSnapshot> {
        synchronized(lock) {
            val copy = snapshots.toList()
            snapshots.clear()
            return copy
        }
    }

    fun lastTimestamp(): Long? {
        synchronized(lock) {
            return snapshots.lastOrNull()?.timestamp
        }
    }

    fun clear() {
        synchronized(lock) {
            snapshots.clear()
        }
    }

    fun flushToCsv(context: Context, label: CrashDataLabel): File {
        return SensorSnapshotCsvWriter.writeCrashLog(context, snapshot(), label)
    }

    private fun trimOlderThan(cutoffTimestampNs: Long) {
        val removeCount = snapshots.indexOfFirst { it.timestamp >= cutoffTimestampNs }
        when {
            removeCount > 0 -> snapshots.subList(0, removeCount).clear()
            removeCount == -1 && snapshots.isNotEmpty() -> snapshots.clear()
        }
    }

    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 50
        private const val DEFAULT_INITIAL_SECONDS = 60
        private const val DEFAULT_INITIAL_CAPACITY = DEFAULT_SAMPLE_RATE_HZ * DEFAULT_INITIAL_SECONDS
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val DEFAULT_WINDOW_NS = DEFAULT_INITIAL_SECONDS * NANOS_PER_SECOND
    }
}
