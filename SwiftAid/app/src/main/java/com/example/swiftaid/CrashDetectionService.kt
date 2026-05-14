package com.example.swiftaid

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

class CrashDetectionService : Service(), SensorEventListener, NativeCrashBridge.CrashCallback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sampleLock = Any()
    private val latestAccel = floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH)
    private val latestGyro = FloatArray(3)
    private val feedAccel = FloatArray(3)
    private val feedGyro = FloatArray(3)

    @Volatile
    private var latestAudioDb = 0f

    private lateinit var sensorManager: SensorManager
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null

    @Volatile
    private var monitoring = false

    @Volatile
    private var audioRunning = false

    private val samplerRunnable = object : Runnable {
        override fun run() {
            if (!monitoring) return

            synchronized(sampleLock) {
                latestAccel.copyInto(feedAccel)
                latestGyro.copyInto(feedGyro)
            }

            NativeCrashBridge.feedSensorData(feedAccel, feedGyro, latestAudioDb)
            sensorHandler?.postDelayed(this, SENSOR_SAMPLE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        createNotificationChannels()
        NativeCrashBridge.nativeInit(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CRASH_CANCELLED -> {
                NativeCrashBridge.resetEngine()
                return START_STICKY
            }
            else -> startMonitoring()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        monitoring = false
        audioRunning = false
        sensorHandler?.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(this)
        sensorThread?.quitSafely()
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
        NativeCrashBridge.nativeShutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(sampleLock) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    latestAccel[0] = event.values[0]
                    latestAccel[1] = event.values[1]
                    latestAccel[2] = event.values[2]
                }
                Sensor.TYPE_GYROSCOPE -> {
                    latestGyro[0] = event.values[0]
                    latestGyro[1] = event.values[1]
                    latestGyro[2] = event.values[2]
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCrashConfirmed() {
        mainHandler.post {
            showCrashOverlay()
        }
    }

    private fun startMonitoring() {
        if (monitoring) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO is required before starting the microphone foreground service")
            stopSelf()
            return
        }

        startAsForeground()
        monitoring = true
        startSensors()
        startAudioMetering()
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_swiftaid)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(MONITOR_NOTIFICATION_ID, notification)
        }
    }

    private fun startSensors() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer available")
            stopSelf()
            return
        }

        sensorThread = HandlerThread("SwiftAidSensors", Process.THREAD_PRIORITY_MORE_FAVORABLE).also {
            it.start()
        }
        sensorHandler = Handler(sensorThread!!.looper)

        sensorManager.registerListener(this, accelerometer, SENSOR_DELAY_US, sensorHandler)
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { gyroscope ->
            sensorManager.registerListener(this, gyroscope, SENSOR_DELAY_US, sensorHandler)
        }

        sensorHandler?.post(samplerRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun startAudioMetering() {
        val minBuffer = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "AudioRecord min buffer unavailable: $minBuffer")
            latestAudioDb = 0f
            return
        }

        val bufferSize = minBuffer.coerceAtLeast(AUDIO_SAMPLE_RATE / 5)
        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * BYTES_PER_PCM_16_SAMPLE)
            .build()

        audioRunning = true
        audioThread = thread(name = "SwiftAidAudioMeter", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val record = audioRecord ?: return@thread
            val buffer = ShortArray(bufferSize)

            runCatching { record.startRecording() }
                .onFailure {
                    Log.e(TAG, "Unable to start AudioRecord", it)
                    audioRunning = false
                }

            while (audioRunning) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    latestAudioDb = estimateDb(buffer, read)
                }
            }
        }
    }

    private fun estimateDb(buffer: ShortArray, length: Int): Float {
        var sumSquares = 0.0
        for (i in 0 until length) {
            val normalized = buffer[i] / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }

        val rms = sqrt(sumSquares / length.coerceAtLeast(1))
        return if (rms > 0.0) {
            (20.0 * log10(rms) + AUDIO_DBFS_TO_SPL_OFFSET).toFloat()
        } else {
            0f
        }
    }

    private fun showCrashOverlay() {
        val overlayIntent = Intent(this, SOSOverlayActivity::class.java)
            .setAction(SOSOverlayActivity.ACTION_CRASH_CONFIRMED)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            SOS_OVERLAY_REQUEST_CODE,
            overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CRASH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_swiftaid)
            .setContentTitle(getString(R.string.crash_notification_title))
            .setContentText(getString(R.string.crash_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(CRASH_NOTIFICATION_ID, notification)
        runCatching { startActivity(overlayIntent) }
            .onFailure { Log.w(TAG, "Direct SOS overlay launch was blocked; full-screen intent posted", it) }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                MONITOR_CHANNEL_ID,
                getString(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CRASH_CHANNEL_ID,
                getString(R.string.crash_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )
    }

    companion object {
        private const val TAG = "CrashDetectionService"
        private const val ACTION_START = "com.example.swiftaid.action.START_MONITORING"
        private const val ACTION_STOP = "com.example.swiftaid.action.STOP_MONITORING"
        private const val ACTION_CRASH_CANCELLED = "com.example.swiftaid.action.CRASH_CANCELLED"
        private const val MONITOR_CHANNEL_ID = "swift_aid_monitor"
        private const val CRASH_CHANNEL_ID = "swift_aid_crash"
        private const val MONITOR_NOTIFICATION_ID = 1001
        private const val CRASH_NOTIFICATION_ID = 1002
        private const val SOS_OVERLAY_REQUEST_CODE = 2001
        private const val SENSOR_DELAY_US = 20_000
        private const val SENSOR_SAMPLE_MS = 20L
        private const val AUDIO_SAMPLE_RATE = 8_000
        private const val BYTES_PER_PCM_16_SAMPLE = 2
        private const val AUDIO_DBFS_TO_SPL_OFFSET = 120.0

        fun start(context: Context) {
            val intent = Intent(context, CrashDetectionService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CrashDetectionService::class.java).setAction(ACTION_STOP))
        }

        fun notifyCrashCancelled(context: Context) {
            context.startService(
                Intent(context, CrashDetectionService::class.java).setAction(ACTION_CRASH_CANCELLED)
            )
        }
    }
}
