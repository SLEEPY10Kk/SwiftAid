package com.example.swiftaid.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlin.concurrent.thread

class MeshSosReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MeshRelayService.ACTION_MESH_SOS_RECEIVED) return
        val packetJson = intent.getStringExtra(MeshRelayService.EXTRA_PACKET_JSON) ?: return
        val pendingResult = goAsync()

        thread(name = "SwiftAidMeshHandoff", isDaemon = true) {
            val wakeLock = context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SwiftAid:MeshHandoff")
                .apply {
                    setReferenceCounted(false)
                    acquire(30_000L)
                }

            try {
                val packet = MeshSosPacket.fromJson(packetJson) ?: return@thread
                if (!markHandled(context, packet.packetId)) return@thread

                MeshLocalAlertPlayer.start(context)
                val result = MeshAuthoritySmsDispatcher.dispatch(context, packet)
                Log.i(
                    TAG,
                    "Mesh SOS handoff handled: packet=${packet.packetId}, contacts=${result.attemptedContacts}, sentParts=${result.sentParts}"
                )
            } catch (throwable: Throwable) {
                Log.w(TAG, "Unable to hand off mesh SOS packet", throwable)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private fun markHandled(context: Context, packetId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val handled = prefs.getStringSet(KEY_HANDLED_PACKET_IDS, emptySet()).orEmpty()
        if (packetId in handled) return false

        val updated = (handled + packetId).toList().takeLast(MAX_HANDLED_IDS).toSet()
        prefs.edit().putStringSet(KEY_HANDLED_PACKET_IDS, updated).apply()
        return true
    }

    companion object {
        private const val TAG = "MeshSosReceiver"
        private const val PREFS_NAME = "swift_aid_mesh_receiver"
        private const val KEY_HANDLED_PACKET_IDS = "handled_packet_ids"
        private const val MAX_HANDLED_IDS = 100
    }
}
