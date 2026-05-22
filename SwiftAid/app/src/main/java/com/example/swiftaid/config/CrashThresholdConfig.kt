package com.example.swiftaid.config

import org.json.JSONObject

data class CrashThresholdConfig(
    val configVersion: Int,
    val impactThresholdG: Float,
    val audioCrashDb: Float,
    val stillnessStdDevG: Float
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put(KEY_CONFIG_VERSION, configVersion)
            .put(KEY_IMPACT_THRESHOLD_G, impactThresholdG.toDouble())
            .put(KEY_AUDIO_CRASH_DB, audioCrashDb.toDouble())
            .put(KEY_STILLNESS_STDDEV_G, stillnessStdDevG.toDouble())
    }

    companion object {
        const val KEY_CONFIG_VERSION = "config_version"
        const val KEY_IMPACT_THRESHOLD_G = "impact_threshold_g"
        const val KEY_AUDIO_CRASH_DB = "audio_crash_db"
        const val KEY_STILLNESS_STDDEV_G = "stillness_stddev_g"

        val DEFAULT = CrashThresholdConfig(
            configVersion = 1,
            impactThresholdG = 10.0f,
            audioCrashDb = 100.0f,
            stillnessStdDevG = 0.5f
        )

        fun fromJson(json: JSONObject): CrashThresholdConfig {
            return CrashThresholdConfig(
                configVersion = json.optInt(KEY_CONFIG_VERSION, DEFAULT.configVersion),
                impactThresholdG = json.optDouble(
                    KEY_IMPACT_THRESHOLD_G,
                    DEFAULT.impactThresholdG.toDouble()
                ).toFloat(),
                audioCrashDb = json.optDouble(
                    KEY_AUDIO_CRASH_DB,
                    DEFAULT.audioCrashDb.toDouble()
                ).toFloat(),
                stillnessStdDevG = json.optDouble(
                    KEY_STILLNESS_STDDEV_G,
                    DEFAULT.stillnessStdDevG.toDouble()
                ).toFloat()
            )
        }
    }
}
