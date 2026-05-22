package com.example.swiftaid

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

object DefaultDialerRoleHelper {
    fun isDialerRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
    }

    fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            telecomManager.defaultDialerPackage == context.packageName
        }
    }

    fun createDefaultDialerRoleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val roleManager = context.getSystemService(RoleManager::class.java)
        return if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            null
        }
    }

    fun requestDefaultDialerRole(activity: Activity, requestCode: Int): Boolean {
        val intent = createDefaultDialerRoleRequestIntent(activity) ?: return false
        activity.startActivityForResult(intent, requestCode)
        return true
    }
}
