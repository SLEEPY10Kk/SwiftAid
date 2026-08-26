package com.example.swiftaid.emergency

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.swiftaid.EmergencySmsDispatcher
import com.example.swiftaid.firebase.FirebaseSosEventManager
import com.example.swiftaid.offline.OfflineSosPayload
import java.util.concurrent.atomic.AtomicBoolean

object EmergencyRelayCoordinator {
    private const val TAG = "EmergencyRelayCoordinator"
    private const val MAX_RELAY_DEPTH = 2
    private const val RELAY_UPLOAD_TIMEOUT_MS = 4_000L

    fun handleIncomingPayload(
        context: Context,
        payload: OfflineSosPayload,
        onComplete: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        PendingSosSyncManager.initialize(appContext)

        val packet = EmergencyConnectionPacket.fromRelayPayload(payload)
        if (!NetworkStatus.isOnline(appContext)) {
            escalateOrQueue(appContext, packet, reason = "relay_offline")
            onComplete()
            return
        }

        val completed = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (completed.compareAndSet(false, true)) {
                escalateOrQueue(appContext, packet, reason = "relay_upload_timeout")
                onComplete()
            }
        }
        handler.postDelayed(timeoutRunnable, RELAY_UPLOAD_TIMEOUT_MS)

        FirebaseSosEventManager.uploadEmergencyPacket(
            context = appContext,
            packet = packet,
            communicationLevel = EmergencyCommunicationLevel.SMS_RELAY.name,
            deliveryState = "UPLOADED_BY_RELAY",
            smsResult = null
        ) { delivered ->
            if (!completed.compareAndSet(false, true)) {
                if (delivered) {
                    PendingSosStore.remove(appContext, packet.eventId)
                }
                return@uploadEmergencyPacket
            }

            handler.removeCallbacks(timeoutRunnable)
            if (delivered) {
                Log.i(TAG, "Relay SOS uploaded: ${packet.eventId}")
            } else {
                escalateOrQueue(appContext, packet, reason = "relay_upload_failed")
            }
            onComplete()
        }
    }

    private fun escalateOrQueue(context: Context, packet: EmergencyConnectionPacket, reason: String) {
        val nextRelayDepth = packet.relayDepth + 1
        if (nextRelayDepth <= MAX_RELAY_DEPTH) {
            val relayMessage = packet.relayMessage(nextRelayDepth)
            val smsResult = EmergencySmsDispatcher.dispatchMessage(
                context = context,
                message = relayMessage,
                excludeNumbers = listOfNotNull(packet.sender)
            )
            PendingSosStore.enqueue(
                context = context,
                packet = packet.copy(
                    relayDepth = nextRelayDepth,
                    smsMessage = relayMessage
                ),
                reason = if (smsResult.sentParts > 0) "relay_escalated_sms" else reason
            )
            Log.i(
                TAG,
                "Relay escalation for ${packet.eventId}: sentParts=${smsResult.sentParts}, nextDepth=$nextRelayDepth"
            )
        } else {
            PendingSosStore.enqueue(context, packet, reason = "relay_depth_exhausted")
            Log.i(TAG, "Relay depth exhausted for ${packet.eventId}; queued for pending sync")
        }

        PendingSosSyncManager.process(context)
    }
}
