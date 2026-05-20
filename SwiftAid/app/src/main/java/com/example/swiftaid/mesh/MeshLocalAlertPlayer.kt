package com.example.swiftaid.mesh

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

object MeshLocalAlertPlayer {
    private const val TAG = "MeshLocalAlertPlayer"
    private const val SAMPLE_RATE = 22_050
    private const val SWEEP_SECONDS = 2
    private const val SWEEP_SAMPLES = SAMPLE_RATE * SWEEP_SECONDS
    private const val ALERT_MS = 20_000L

    @Volatile
    private var running = false

    private var previousAlarmVolume: Int? = null
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    fun start(context: Context) {
        stop(context)

        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        runCatching {
            previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }

        running = true
        playbackThread = thread(name = "SwiftAidMeshAlert", isDaemon = true) {
            runCatching {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val minBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                val startedAt = System.currentTimeMillis()
                val buffer = ShortArray(minBuffer / 2)
                var phase = 0.0
                var sampleIndex = 0L
                while (running && System.currentTimeMillis() - startedAt < ALERT_MS) {
                    for (i in buffer.indices) {
                        val sweep = (sampleIndex % SWEEP_SAMPLES).toDouble() / SWEEP_SAMPLES.toDouble()
                        val frequency = if (sweep < 0.5) {
                            700.0 + 950.0 * (sweep * 2.0)
                        } else {
                            1650.0 - 950.0 * ((sweep - 0.5) * 2.0)
                        }
                        phase += 2.0 * PI * frequency / SAMPLE_RATE
                        buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.42).toInt().toShort()
                        sampleIndex += 1
                    }
                    track.write(buffer, 0, buffer.size)
                }
            }.onFailure { throwable ->
                Log.w(TAG, "Unable to play mesh SOS alert", throwable)
            }

            running = false
            releaseTrack()
            restoreAlarmVolume(appContext)
        }
    }

    fun stop(context: Context) {
        running = false
        releaseTrack()
        restoreAlarmVolume(context.applicationContext)
    }

    private fun releaseTrack() {
        audioTrack?.runCatching {
            pause()
            flush()
            release()
        }
        audioTrack = null
    }

    private fun restoreAlarmVolume(context: Context) {
        val volume = previousAlarmVolume ?: return
        previousAlarmVolume = null
        runCatching {
            context.getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        }
    }
}
