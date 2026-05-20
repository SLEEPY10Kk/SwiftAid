package com.example.swiftaid.mesh

import android.content.Context

object MeshVolunteerSettings {
    private const val PREFS_NAME = "swift_aid_mesh"
    private const val KEY_VOLUNTEER_ENABLED = "volunteer_mesh_enabled"

    fun isEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOLUNTEER_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VOLUNTEER_ENABLED, enabled)
            .apply()
    }
}
