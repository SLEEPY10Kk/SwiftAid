package com.example.policeapp.data

import android.content.Context
import com.example.policeapp.data.model.ResponderProfile

object ResponderSession {
    private const val PREFS = "responder_session"
    private const val KEY_ID = "id"
    private const val KEY_TYPE = "type"
    private const val KEY_NAME = "name"
    private const val KEY_PHONE = "phone"
    private const val KEY_ADDRESS = "address"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"

    fun save(context: Context, profile: ResponderProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ID, profile.id)
            .putString(KEY_TYPE, profile.serviceType)
            .putString(KEY_NAME, profile.name)
            .putString(KEY_PHONE, profile.phoneNumber)
            .putString(KEY_ADDRESS, profile.address)
            .putFloat(KEY_LATITUDE, profile.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, profile.longitude.toFloat())
            .apply()
    }

    fun load(context: Context): ResponderProfile? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_ID, "").orEmpty()
        if (id.isBlank()) return null
        return ResponderProfile(
            id = id,
            serviceType = prefs.getString(KEY_TYPE, "").orEmpty(),
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            phoneNumber = prefs.getString(KEY_PHONE, "").orEmpty(),
            address = prefs.getString(KEY_ADDRESS, "").orEmpty(),
            latitude = prefs.getFloat(KEY_LATITUDE, 0f).toDouble(),
            longitude = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble(),
            active = true
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
