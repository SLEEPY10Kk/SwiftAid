package com.example.swiftaid.offline

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.swiftaid.R

object OfflineSosAlarmPlayer {
    private val lock = Any()
    private var mediaPlayer: MediaPlayer? = null

    fun start(context: Context) {
        synchronized(lock) {
            if (mediaPlayer?.isPlaying == true) return
            maximizeAlarmVolume(context)
            mediaPlayer = runCatching {
                val assetFileDescriptor = context.resources.openRawResourceFd(R.raw.offline_sos_alarm)
                    ?: error("offline_sos_alarm raw resource is unavailable")
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(
                        assetFileDescriptor.fileDescriptor,
                        assetFileDescriptor.startOffset,
                        assetFileDescriptor.length
                    )
                    assetFileDescriptor.close()
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
    }

    fun stop() {
        synchronized(lock) {
            mediaPlayer?.let { player ->
                runCatching {
                    player.stop()
                    player.release()
                }
            }
            mediaPlayer = null
        }
    }

    private fun maximizeAlarmVolume(context: Context) {
        runCatching {
            val audioManager = context.getSystemService(AudioManager::class.java)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }
    }
}
