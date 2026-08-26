package com.example.swiftaid.emergency

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.swiftaid.firebase.FirebaseSosEventManager
import java.util.concurrent.atomic.AtomicInteger

object PendingSosSyncManager {
    private const val TAG = "PendingSosSyncManager"

    @Volatile
    private var initialized = false

    @Volatile
    private var syncing = false

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    registerNetworkCallback(appContext)
                    initialized = true
                }
            }
        }
        process(appContext)
    }

    fun process(context: Context) {
        val appContext = context.applicationContext
        if (syncing || !NetworkStatus.isOnline(appContext)) return

        val records = PendingSosStore.snapshot(appContext)
        if (records.isEmpty()) return

        syncing = true
        val remaining = AtomicInteger(records.size)
        records.forEach { record ->
            PendingSosStore.markAttempt(appContext, record.packet.eventId)
            FirebaseSosEventManager.uploadEmergencyPacket(
                context = appContext,
                packet = record.packet,
                communicationLevel = EmergencyCommunicationLevel.PENDING_SYNC.name,
                deliveryState = "SYNCED_AFTER_RECOVERY",
                smsResult = null
            ) { delivered ->
                if (delivered) {
                    PendingSosStore.remove(appContext, record.packet.eventId)
                    Log.i(TAG, "Pending SOS synced: ${record.packet.eventId}")
                } else {
                    Log.w(TAG, "Pending SOS sync failed: ${record.packet.eventId}")
                }

                if (remaining.decrementAndGet() == 0) {
                    syncing = false
                }
            }
        }
    }

    private fun registerNetworkCallback(context: Context) {
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)
                .registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            process(context)
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities
                        ) {
                            if (
                                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            ) {
                                process(context)
                            }
                        }
                    }
                )
        }.onFailure { throwable ->
            Log.w(TAG, "Unable to register pending SOS network callback", throwable)
        }
    }
}
