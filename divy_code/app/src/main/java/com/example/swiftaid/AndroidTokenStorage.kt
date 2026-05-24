package com.example.swiftaid

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clearTokens()
}

class AndroidTokenStorage(context: Context) : TokenStorage {
    private val appContext = context.applicationContext
    private val prefs by lazy { createEncryptedPrefs() }

    private fun createEncryptedPrefs(): android.content.SharedPreferences {
        return try {
            buildEncryptedPrefs()
        } catch (first: Throwable) {
            Log.w("AndroidTokenStorage", "Encrypted token store unreadable, clearing and rebuilding", first)
            runCatching { appContext.deleteSharedPreferences(SECURE_PREFS_NAME) }
            runCatching { appContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit() }
            buildEncryptedPrefs()
        }
    }

    private fun buildEncryptedPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    override fun getAccessToken(): String? = prefs.getString("access_token", null)

    override fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    override fun clearTokens() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "secure_tokens"
    }
}
