package com.example.swiftaid.emergency

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.swiftaid.EmergencySmsDispatchResult
import com.example.swiftaid.EmergencySmsDispatcher
import com.example.swiftaid.SosLocationResult
import com.example.swiftaid.firebase.FirebaseSosEventManager
import java.util.concurrent.atomic.AtomicBoolean

enum class EmergencyCommunicationLevel {
    ONLINE_CLOUD,
    SMS_RELAY,
    PENDING_SYNC
}

data class EmergencyConnectionResult(
    val level: EmergencyCommunicationLevel,
    val cloudDelivered: Boolean,
    val smsResult: EmergencySmsDispatchResult,
    val pendingStored: Boolean
)

object EmergencyConnectionCoordinator {
    private const val CLOUD_DISPATCH_TIMEOUT_MS = 4_000L

    fun dispatch(
        context: Context,
        locationResult: SosLocationResult,
        sosType: String,
        onComplete: (EmergencyConnectionResult) -> Unit
    ) {
        val appContext = context.applicationContext
        PendingSosSyncManager.initialize(appContext)

        if (!NetworkStatus.isOnline(appContext)) {
            val contacts = EmergencySmsDispatcher.getEmergencyContacts(appContext)
            val cachedResponders = cachedRespondersForSms(appContext, locationResult)
            val packet = EmergencyConnectionPacket.fromLocation(
                context = appContext,
                locationResult = locationResult,
                sosType = sosType,
                emergencyContacts = contacts,
                nearestResponders = cachedResponders.responders,
                responderCacheUpdatedAtMillis = cachedResponders.cachedAtMillis
            )
            sendSmsFallback(appContext, packet, onComplete)
            return
        }

        val contacts = EmergencySmsDispatcher.getEmergencyContacts(appContext)
        val cachedResponders = cachedRespondersForSms(appContext, locationResult)
        val packet = EmergencyConnectionPacket.fromLocation(
            context = appContext,
            locationResult = locationResult,
            sosType = sosType,
            emergencyContacts = contacts,
            nearestResponders = cachedResponders.responders,
            responderCacheUpdatedAtMillis = cachedResponders.cachedAtMillis
        )
        dispatchPreparedPacket(appContext, packet, contacts.size, onComplete)
    }

    private fun cachedRespondersForSms(
        context: Context,
        locationResult: SosLocationResult
    ): CachedResponderSelection {
        val location = locationResult.location
        return ResponderCacheStore.nearestForSms(
            context = context,
            latitude = location?.latitude,
            longitude = location?.longitude,
            limitPerType = 1
        )
    }

    private fun dispatchPreparedPacket(
        appContext: Context,
        packet: EmergencyConnectionPacket,
        contactCount: Int,
        onComplete: (EmergencyConnectionResult) -> Unit
    ) {
        val completed = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (completed.compareAndSet(false, true)) {
                sendSmsFallback(appContext, packet, onComplete)
            }
        }
        handler.postDelayed(timeoutRunnable, CLOUD_DISPATCH_TIMEOUT_MS)

        FirebaseSosEventManager.uploadEmergencyPacket(
            context = appContext,
            packet = packet,
            communicationLevel = EmergencyCommunicationLevel.ONLINE_CLOUD.name,
            deliveryState = "ONLINE_DISPATCHED",
            smsResult = EmergencySmsDispatchResult(contactCount, 0, packet.smsMessage)
        ) { delivered ->
            if (!completed.compareAndSet(false, true)) {
                if (delivered) {
                    PendingSosStore.remove(appContext, packet.eventId)
                }
                return@uploadEmergencyPacket
            }

            handler.removeCallbacks(timeoutRunnable)
            if (delivered) {
                onComplete(
                    EmergencyConnectionResult(
                        level = EmergencyCommunicationLevel.ONLINE_CLOUD,
                        cloudDelivered = true,
                        smsResult = EmergencySmsDispatchResult(contactCount, 0, packet.smsMessage),
                        pendingStored = false
                    )
                )
            } else {
                sendSmsFallback(appContext, packet, onComplete)
            }
        }
    }

    private fun sendSmsFallback(
        context: Context,
        packet: EmergencyConnectionPacket,
        onComplete: (EmergencyConnectionResult) -> Unit
    ) {
        val smsResult = EmergencySmsDispatcher.dispatchMessage(context, packet.smsMessage)
        PendingSosStore.enqueue(context, packet, reason = "cloud_unavailable_or_failed")

        onComplete(
            EmergencyConnectionResult(
                level = if (smsResult.sentParts > 0) {
                    EmergencyCommunicationLevel.SMS_RELAY
                } else {
                    EmergencyCommunicationLevel.PENDING_SYNC
                },
                cloudDelivered = false,
                smsResult = smsResult,
                pendingStored = true
            )
        )

        PendingSosSyncManager.process(context)
    }
}
