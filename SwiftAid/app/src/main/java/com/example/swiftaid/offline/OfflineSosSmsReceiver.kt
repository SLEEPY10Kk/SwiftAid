package com.example.swiftaid.offline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.swiftaid.emergency.EmergencyRelayCoordinator

class OfflineSosSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val sender = messages.firstOrNull()?.originatingAddress
        val payload = OfflineSosPayload.parse(body, sender) ?: return

        Log.i(TAG, "Offline SOS SMS received from ${sender.orEmpty()}")
        OfflineSosAlertController.showAlert(context.applicationContext, payload)
        val pendingResult = goAsync()
        EmergencyRelayCoordinator.handleIncomingPayload(context.applicationContext, payload) {
            pendingResult.finish()
        }

        if (isOrderedBroadcast) {
            runCatching { abortBroadcast() }
        }
    }

    companion object {
        private const val TAG = "OfflineSosSmsReceiver"
    }
}
