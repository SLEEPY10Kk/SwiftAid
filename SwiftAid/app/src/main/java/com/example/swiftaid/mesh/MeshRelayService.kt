package com.example.swiftaid.mesh

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.swiftaid.R
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionType
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

class MeshRelayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val connectedEndpoints = mutableSetOf<String>()
    private val pendingEndpoints = mutableSetOf<String>()
    private val activePackets = linkedMapOf<String, MeshSosPacket>()
    private val seenPacketIds = object : LinkedHashMap<String, Long>(MAX_SEEN_PACKET_IDS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_SEEN_PACKET_IDS
        }
    }

    private lateinit var connectionsClient: ConnectionsClient
    private var nearbyMode: NearbyMode? = null
    private var passiveRequested = false

    private val relayStopRunnable = Runnable {
        activePackets.clear()
        if (passiveRequested && MeshVolunteerSettings.isEnabled(this)) {
            startNearby(lowPower = true)
            updateForegroundNotification(active = false)
        } else {
            stopSelf()
        }
    }

    private val rebroadcastRunnable = object : Runnable {
        override fun run() {
            if (activePackets.isEmpty()) return
            sendActivePacketsToConnectedEndpoints()
            handler.postDelayed(this, REBROADCAST_INTERVAL_MS)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (endpointId in connectedEndpoints || endpointId in pendingEndpoints) return

            pendingEndpoints += endpointId
            connectionsClient.requestConnection(localEndpointName(), endpointId, connectionLifecycleCallback)
                .addOnFailureListener { throwable ->
                    pendingEndpoints -= endpointId
                    Log.w(TAG, "Unable to request mesh connection: $endpointId", throwable)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            pendingEndpoints -= endpointId
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            pendingEndpoints += endpointId
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { throwable ->
                    pendingEndpoints -= endpointId
                    Log.w(TAG, "Unable to accept mesh connection: $endpointId", throwable)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingEndpoints -= endpointId
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoints += endpointId
                sendActivePackets(endpointId)
            } else {
                connectedEndpoints -= endpointId
                connectionsClient.disconnectFromEndpoint(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints -= endpointId
            pendingEndpoints -= endpointId
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val rawJson = payload.asBytes()?.let { String(it, Charsets.UTF_8) } ?: return
            val packet = MeshSosPacket.fromJson(rawJson) ?: return
            handleIncomingPacket(packet)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        connectionsClient = Nearby.getConnectionsClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(active = false)

        when (intent?.action) {
            ACTION_START_PASSIVE -> {
                passiveRequested = true
                startPassiveListening()
            }

            ACTION_BROADCAST_LOCAL_SOS -> {
                val packet = intent.getStringExtra(EXTRA_PACKET_JSON)
                    ?.let(MeshSosPacket::fromJson)
                if (packet != null) {
                    markSeen(packet.packetId)
                    activateRipple(packet)
                }
            }

            ACTION_STOP -> {
                passiveRequested = false
                stopSelf()
            }

            else -> {
                passiveRequested = true
                startPassiveListening()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopNearby()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPassiveListening() {
        if (!MeshVolunteerSettings.isEnabled(this)) {
            stopSelf()
            return
        }
        if (activePackets.isNotEmpty()) return
        if (!hasNearbyPermissions()) {
            Log.w(TAG, "Nearby permissions missing; mesh passive listening not started")
            return
        }
        startNearby(lowPower = true)
        updateForegroundNotification(active = false)
    }

    private fun activateRipple(packet: MeshSosPacket) {
        if (!hasNearbyPermissions()) {
            Log.w(TAG, "Nearby permissions missing; mesh SOS ripple not started")
            if (!passiveRequested) stopSelf()
            return
        }

        if (packet.canRelay()) {
            activePackets[packet.packetId] = packet
        }
        if (activePackets.isEmpty()) {
            if (!passiveRequested) stopSelf()
            return
        }

        startNearby(lowPower = false)
        sendActivePacketsToConnectedEndpoints()
        handler.removeCallbacks(relayStopRunnable)
        handler.removeCallbacks(rebroadcastRunnable)
        handler.postDelayed(relayStopRunnable, ACTIVE_RIPPLE_MS)
        handler.postDelayed(rebroadcastRunnable, REBROADCAST_INTERVAL_MS)
        updateForegroundNotification(active = true)
    }

    private fun handleIncomingPacket(packet: MeshSosPacket) {
        if (!MeshVolunteerSettings.isEnabled(this)) return
        if (!markSeen(packet.packetId)) return

        sendBroadcast(
            Intent(this, MeshSosReceiver::class.java)
                .setAction(ACTION_MESH_SOS_RECEIVED)
                .putExtra(EXTRA_PACKET_JSON, packet.toJson())
        )

        if (packet.ttl <= 1) return
        activateRipple(packet.relayed())
    }

    private fun startNearby(lowPower: Boolean) {
        val desiredMode = if (lowPower) NearbyMode.PASSIVE else NearbyMode.ACTIVE
        if (nearbyMode == desiredMode) return

        stopNearby()
        nearbyMode = desiredMode

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .setLowPower(lowPower)
            .setConnectionType(ConnectionType.BALANCED)
            .build()
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .setLowPower(lowPower)
            .build()

        connectionsClient.startAdvertising(
            localEndpointName(),
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnFailureListener { throwable ->
            Log.w(TAG, "Unable to start mesh advertising", throwable)
        }

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnFailureListener { throwable ->
            Log.w(TAG, "Unable to start mesh discovery", throwable)
        }
    }

    private fun stopNearby() {
        connectedEndpoints.clear()
        pendingEndpoints.clear()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        nearbyMode = null
    }

    private fun sendActivePacketsToConnectedEndpoints() {
        connectedEndpoints.toList().forEach(::sendActivePackets)
    }

    private fun sendActivePackets(endpointId: String) {
        activePackets.values.forEach { packet ->
            connectionsClient.sendPayload(
                endpointId,
                Payload.fromBytes(packet.toJson().toByteArray(Charsets.UTF_8))
            ).addOnFailureListener { throwable ->
                Log.w(TAG, "Unable to send mesh SOS packet to $endpointId", throwable)
            }
        }
    }

    private fun markSeen(packetId: String): Boolean {
        if (seenPacketIds.containsKey(packetId)) return false
        seenPacketIds[packetId] = System.currentTimeMillis()
        return true
    }

    private fun hasNearbyPermissions(): Boolean {
        val required = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q until Build.VERSION_CODES.S) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        return required.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun startAsForeground(active: Boolean) {
        val notification = buildNotification(active)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MESH_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(MESH_NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(active: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(MESH_NOTIFICATION_ID, buildNotification(active))
    }

    private fun buildNotification(active: Boolean): Notification {
        return NotificationCompat.Builder(this, MESH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_swiftaid)
            .setContentTitle(getString(R.string.mesh_notification_title))
            .setContentText(
                getString(
                    if (active) {
                        R.string.mesh_notification_active
                    } else {
                        R.string.mesh_notification_passive
                    }
                )
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                MESH_CHANNEL_ID,
                getString(R.string.mesh_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun localEndpointName(): String = "${getString(R.string.app_name)}-${Build.MODEL}"

    private enum class NearbyMode {
        PASSIVE,
        ACTIVE
    }

    companion object {
        const val ACTION_MESH_SOS_RECEIVED = "com.example.swiftaid.action.MESH_SOS_RECEIVED"
        const val EXTRA_PACKET_JSON = "packet_json"

        private const val TAG = "MeshRelayService"
        private const val ACTION_START_PASSIVE = "com.example.swiftaid.action.START_MESH_PASSIVE"
        private const val ACTION_BROADCAST_LOCAL_SOS = "com.example.swiftaid.action.BROADCAST_LOCAL_MESH_SOS"
        private const val ACTION_STOP = "com.example.swiftaid.action.STOP_MESH"
        private const val SERVICE_ID = "com.example.swiftaid.mesh"
        private const val MESH_CHANNEL_ID = "swift_aid_mesh"
        private const val MESH_NOTIFICATION_ID = 1301
        private const val ACTIVE_RIPPLE_MS = 30_000L
        private const val REBROADCAST_INTERVAL_MS = 5_000L
        private const val MAX_SEEN_PACKET_IDS = 256
        private val STRATEGY = Strategy.P2P_CLUSTER

        fun startPassive(context: Context) {
            startMeshService(context, Intent(context, MeshRelayService::class.java).setAction(ACTION_START_PASSIVE))
        }

        fun broadcastLocalSos(context: Context, packet: MeshSosPacket) {
            startMeshService(
                context,
                Intent(context, MeshRelayService::class.java)
                    .setAction(ACTION_BROADCAST_LOCAL_SOS)
                    .putExtra(EXTRA_PACKET_JSON, packet.toJson())
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshRelayService::class.java).setAction(ACTION_STOP))
        }

        private fun startMeshService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
