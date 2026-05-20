package com.example.swiftaid.logging

import android.content.Context
import java.io.File

class SensorSnapshotBuffer(
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY
) {
    private val lock = Any()
    private val snapshots = ArrayList<SensorSnapshot>(initialCapacity.coerceAtLeast(1))

    fun add(snapshot: SensorSnapshot) {
        synchronized(lock) {
            snapshots += snapshot
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

    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 50
        private const val DEFAULT_INITIAL_SECONDS = 30
        private const val DEFAULT_INITIAL_CAPACITY = DEFAULT_SAMPLE_RATE_HZ * DEFAULT_INITIAL_SECONDS
    }
}
