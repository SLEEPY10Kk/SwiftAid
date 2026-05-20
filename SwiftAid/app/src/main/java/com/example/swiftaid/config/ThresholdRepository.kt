package com.example.swiftaid.config

import android.content.Context
import com.example.swiftaid.NativeCrashBridge
import org.json.JSONObject
import java.io.File

class ThresholdRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val localConfigFile = File(appContext.filesDir, LOCAL_CONFIG_FILE_NAME)

    fun currentConfig(): CrashThresholdConfig {
        return CrashThresholdConfig(
            configVersion = preferences.getInt(
                CrashThresholdConfig.KEY_CONFIG_VERSION,
                CrashThresholdConfig.DEFAULT.configVersion
            ),
            impactThresholdG = preferences.getFloat(
                CrashThresholdConfig.KEY_IMPACT_THRESHOLD_G,
                CrashThresholdConfig.DEFAULT.impactThresholdG
            ),
            audioCrashDb = preferences.getFloat(
                CrashThresholdConfig.KEY_AUDIO_CRASH_DB,
                CrashThresholdConfig.DEFAULT.audioCrashDb
            ),
            stillnessStdDevG = preferences.getFloat(
                CrashThresholdConfig.KEY_STILLNESS_STDDEV_G,
                CrashThresholdConfig.DEFAULT.stillnessStdDevG
            )
        )
    }

    fun saveIfNewer(config: CrashThresholdConfig): Boolean {
        if (config.configVersion <= currentConfig().configVersion) {
            return false
        }

        preferences.edit()
            .putInt(CrashThresholdConfig.KEY_CONFIG_VERSION, config.configVersion)
            .putFloat(CrashThresholdConfig.KEY_IMPACT_THRESHOLD_G, config.impactThresholdG)
            .putFloat(CrashThresholdConfig.KEY_AUDIO_CRASH_DB, config.audioCrashDb)
            .putFloat(CrashThresholdConfig.KEY_STILLNESS_STDDEV_G, config.stillnessStdDevG)
            .apply()
        localConfigFile.writeText(config.toJson().toString(2))
        applyToNative(config)
        return true
    }

    fun applyCurrentToNative() {
        val fileConfig = readLocalFileConfig()
        if (fileConfig != null && fileConfig.configVersion > currentConfig().configVersion) {
            saveIfNewer(fileConfig)
        } else {
            applyToNative(currentConfig())
        }
    }

    private fun readLocalFileConfig(): CrashThresholdConfig? {
        if (!localConfigFile.exists()) return null
        return runCatching {
            CrashThresholdConfig.fromJson(JSONObject(localConfigFile.readText()))
        }.getOrNull()
    }

    private fun applyToNative(config: CrashThresholdConfig) {
        NativeCrashBridge.updateThresholds(
            config.configVersion,
            config.impactThresholdG,
            config.audioCrashDb,
            config.stillnessStdDevG
        )
    }

    companion object {
        private const val PREFS_NAME = "swift_aid_thresholds"
        private const val LOCAL_CONFIG_FILE_NAME = "local_thresholds.json"
    }
}
