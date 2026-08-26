package com.example.swiftaid

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

class SwiftAidCallManager(private val context: Context) {
    private val telecomManager: TelecomManager by lazy {
        context.getSystemService(TelecomManager::class.java)
    }

    @SuppressLint("MissingPermission")
    fun placeCall(phoneNumber: String): Boolean {
        if (!DefaultDialerRoleHelper.isDefaultDialer(context)) {
            return false
        }

        val callUri = phoneNumber.toTelUri()
        val phoneAccountHandle = resolvePhoneAccountHandle() ?: return false
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
        }

        /*
         * Emergency numbers such as 112 are handled by the Android framework.
         * Even when SwiftAid is the default dialer, the OS can override the app UI
         * and require user confirmation through the system emergency dialer path.
         * Use TelecomManager.placeCall(); do not use ACTION_CALL for this flow.
         */
        return runCatching {
            telecomManager.placeCall(callUri, extras)
            true
        }.onFailure { throwable ->
            Log.w(TAG, "Unable to place call through TelecomManager", throwable)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun resolvePhoneAccountHandle(): PhoneAccountHandle? {
        telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)?.let { return it }

        return telecomManager.callCapablePhoneAccounts.firstOrNull { handle ->
            telecomManager.getPhoneAccount(handle)
                ?.supportedUriSchemes
                ?.contains(PhoneAccount.SCHEME_TEL) == true
        } ?: telecomManager.callCapablePhoneAccounts.firstOrNull()
    }

    private fun String.toTelUri(): Uri {
        val normalizedNumber = trim()
        return if (normalizedNumber.startsWith("tel:", ignoreCase = true)) {
            Uri.parse(normalizedNumber)
        } else {
            Uri.fromParts(PhoneAccount.SCHEME_TEL, normalizedNumber, null)
        }
    }

    companion object {
        private const val TAG = "SwiftAidCallManager"
    }
}
