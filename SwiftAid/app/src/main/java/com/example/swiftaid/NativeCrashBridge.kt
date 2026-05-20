package com.example.swiftaid

object NativeCrashBridge {
    interface CrashCallback {
        fun onCrashConfirmed()
    }

    external fun nativeInit(callback: CrashCallback)

    external fun feedSensorData(
        accel: FloatArray,
        gyro: FloatArray,
        audioDb: Float
    )

    external fun resetEngine()

    external fun updateThresholds(
        configVersion: Int,
        impactThresholdG: Float,
        audioCrashDb: Float,
        stillnessStdDevG: Float
    )

    external fun nativeShutdown()

    init {
        System.loadLibrary("swiftaid")
    }
}
