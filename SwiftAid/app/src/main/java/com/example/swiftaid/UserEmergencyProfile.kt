package com.example.swiftaid

import android.content.Context
import android.telephony.PhoneNumberUtils

data class UserEmergencyProfile(
    val fullName: String,
    val phone: String
) {
    companion object {
        private const val PREFS_NAME = "swift_aid_user_emergency_profile"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_PHONE = "phone"

        fun load(context: Context): UserEmergencyProfile {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return UserEmergencyProfile(
                fullName = prefs.getString(KEY_FULL_NAME, "").orEmpty(),
                phone = prefs.getString(KEY_PHONE, "").orEmpty()
            )
        }

        fun save(context: Context, fullName: String, phone: String) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FULL_NAME, fullName.trim())
                .putString(KEY_PHONE, normalizePhoneNumber(phone))
                .apply()
        }

        private fun normalizePhoneNumber(phoneNumber: String): String {
            return PhoneNumberUtils.normalizeNumber(phoneNumber).ifBlank {
                phoneNumber.filter { it.isDigit() || it == '+' }
            }
        }
    }
}
