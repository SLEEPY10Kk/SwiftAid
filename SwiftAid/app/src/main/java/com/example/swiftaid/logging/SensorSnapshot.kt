package com.example.swiftaid.logging

data class SensorSnapshot(
    val timestamp: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val lat: Double?,
    val lon: Double?,
    val label: String? = null
)
