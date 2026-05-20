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
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.swiftaid.config.ConfigManager
import com.example.swiftaid.logging.CrashDataLabel
import com.example.swiftaid.logging.CrashDataUploader
import com.example.swiftaid.logging.SensorSnapshot
import com.example.swiftaid.logging.SensorSnapshotBuffer
import com.example.swiftaid.logging.SensorSnapshotCsvWriter
import com.example.swiftaid.mesh.MeshRelayService
import com.example.swiftaid.mesh.MeshVolunteerSettings
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class CrashDetectionService : Service(), SensorEventListener, NativeCrashBridge.CrashCallback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sampleLock = Any()
    private val latestAccel = floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH)
    private val latestGyro = FloatArray(3)
    private val feedAccel = FloatArray(3)
    private val feedGyro = FloatArray(3)
    private val snapshotBuffer = SensorSnapshotBuffer()
    private val pendingCrashLogLock = Any()
    @Volatile
    private var pendingCrashTriggerTimestampNs: Long? = null

    @Volatile
    private var latestAudioDb = 0f

    @Volatile
    private var latestLocation: Location? = null

    private lateinit var sensorManager: SensorManager
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var configManager: ConfigManager? = null

    @Volatile
    private var monitoring = false

    @Volatile
    private var audioRunning = false

    @Volatile
    private var movementLoggingActive = false
    @Volatile
    private var lastMovingElapsedMs = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            latestLocation = result.lastLocation ?: return
        }
    }

    private val samplerRunnable = object : Runnable {
        override fun run() {
            if (!monitoring) return

            synchronized(sampleLock) {
                latestAccel.copyInto(feedAccel)
                latestGyro.copyInto(feedGyro)
            }

            val location = latestLocation
            appendSnapshotIfMoving(location)
            NativeCrashBridge.feedSensorData(feedAccel, feedGyro, latestAudioDb)
            sensorHandler?.postDelayed(this, SENSOR_SAMPLE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        createNotificationChannels()
        NativeCrashBridge.nativeInit(this)
        configManager = ConfigManager(this).also { it.startPeriodicFetch() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CRASH_CANCELLED -> {
                finalizePendingCrashLog(CrashDataLabel.NON_ACCIDENT)
                NativeCrashBridge.resetEngine()
                return START_STICKY
            }
            ACTION_SOS_DISPATCHED -> {
                finalizePendingCrashLog(
                    CrashDataLabel.ACCIDENT,
                    manualSos = intent.getBooleanExtra(EXTRA_MANUAL_SOS, false)
                )
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
        if (!MeshVolunteerSettings.isEnabled(this)) {
            MeshRelayService.stop(this)
        }
        configManager?.stop()
        configManager = null
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
        synchronized(pendingCrashLogLock) {
            pendingCrashTriggerTimestampNs = snapshotBuffer.lastTimestamp() ?: SystemClock.elapsedRealtimeNanos()
        }

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
        snapshotBuffer.clear()
        movementLoggingActive = false
        lastMovingElapsedMs = 0L
        monitoring = true
        startSensors()
        startLocationLogging()
        startAudioMetering()
        if (MeshVolunteerSettings.isEnabled(this)) {
            MeshRelayService.startPassive(this)
        }
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
    private fun startLocationLogging() {
        if (!hasLocationPermission()) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                latestLocation = location
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_SAMPLE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
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

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun appendSnapshotIfMoving(location: Location?) {
        if (pendingCrashTriggerTimestampNs != null) {
            return
        }

        val timestampNs = SystemClock.elapsedRealtimeNanos()
        val moving = isMoving(feedAccel, feedGyro, location)
        val nowElapsedMs = SystemClock.elapsedRealtime()

        if (moving) {
            if (!movementLoggingActive) {
                snapshotBuffer.clear()
                movementLoggingActive = true
            }
            lastMovingElapsedMs = nowElapsedMs
            snapshotBuffer.add(
                SensorSnapshot(
                    timestamp = timestampNs,
                    accelX = feedAccel[0],
                    accelY = feedAccel[1],
                    accelZ = feedAccel[2],
                    gyroX = feedGyro[0],
                    gyroY = feedGyro[1],
                    gyroZ = feedGyro[2],
                    lat = location?.latitude,
                    lon = location?.longitude
                )
            )
        } else if (
            movementLoggingActive &&
            pendingCrashTriggerTimestampNs == null &&
            nowElapsedMs - lastMovingElapsedMs >= STATIONARY_LOG_RESET_MS
        ) {
            movementLoggingActive = false
            snapshotBuffer.clear()
        }
    }

    private fun isMoving(accel: FloatArray, gyro: FloatArray, location: Location?): Boolean {
        if (location?.hasSpeed() == true && location.speed >= DRIVING_SPEED_THRESHOLD_MPS) {
            return true
        }

        val accelMagnitude = sqrt(
            (accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]).toDouble()
        ).toFloat()
        val linearAccel = abs(accelMagnitude - SensorManager.GRAVITY_EARTH)
        val gyroMagnitude = sqrt(
            (gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2]).toDouble()
        ).toFloat()

        return linearAccel >= MOVEMENT_ACCEL_THRESHOLD_MPS2 ||
            gyroMagnitude >= MOVEMENT_GYRO_THRESHOLD_RAD_S
    }

    private fun finalizePendingCrashLog(
        label: CrashDataLabel,
        manualSos: Boolean = false
    ) {
        val pending = synchronized(pendingCrashLogLock) {
            val triggerTimestampNs = pendingCrashTriggerTimestampNs ?: if (manualSos) {
                snapshotBuffer.lastTimestamp() ?: SystemClock.elapsedRealtimeNanos()
            } else {
                null
            }
            pendingCrashTriggerTimestampNs = null
            PendingCrashLog(snapshotBuffer.snapshotAndClear(), triggerTimestampNs)
        }
        val snapshots = pending.snapshots
        if (snapshots.isEmpty() || pending.triggerTimestampNs == null) {
            Log.i(TAG, "No pending crash sensor log to write")
            return
        }
        val triggerTimestampNs = pending.triggerTimestampNs
        val accidentWindowNs = if (manualSos) {
            MANUAL_SOS_ACCIDENT_LABEL_WINDOW_NS
        } else {
            NATIVE_CRASH_ACCIDENT_LABEL_WINDOW_NS
        }

        thread(name = "SwiftAidCrashLogWriter", isDaemon = true) {
            runCatching {
                val labeledSnapshots = labelSnapshots(
                    snapshots = snapshots,
                    finalLabel = label,
                    triggerTimestampNs = triggerTimestampNs,
                    accidentWindowNs = accidentWindowNs
                )
                val file = SensorSnapshotCsvWriter.writeCrashLog(applicationContext, labeledSnapshots, label)
                val uploaded = CrashDataUploader.uploadCrashData(labeledSnapshots, label)
                movementLoggingActive = false
                lastMovingElapsedMs = 0L
                Log.i(
                    TAG,
                    "Crash sensor log written (${label.csvValue}): ${file.absolutePath}; uploaded=$uploaded"
                )
            }.onFailure { throwable ->
                Log.w(TAG, "Unable to write ${label.csvValue} crash sensor log", throwable)
            }
        }
    }

    private data class PendingCrashLog(
        val snapshots: List<SensorSnapshot>,
        val triggerTimestampNs: Long?
    )

    private fun labelSnapshots(
        snapshots: List<SensorSnapshot>,
        finalLabel: CrashDataLabel,
        triggerTimestampNs: Long?,
        accidentWindowNs: Long
    ): List<SensorSnapshot> {
        if (finalLabel == CrashDataLabel.NON_ACCIDENT || triggerTimestampNs == null) {
            return snapshots.map { it.copy(label = CrashDataLabel.NON_ACCIDENT.csvValue) }
        }

        val accidentWindowStartNs = triggerTimestampNs - accidentWindowNs
        return snapshots.map { snapshot ->
            val rowLabel = if (snapshot.timestamp >= accidentWindowStartNs && snapshot.timestamp <= triggerTimestampNs) {
                CrashDataLabel.ACCIDENT
            } else {
                CrashDataLabel.NON_ACCIDENT
            }
            snapshot.copy(label = rowLabel.csvValue)
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
        private const val ACTION_SOS_DISPATCHED = "com.example.swiftaid.action.SOS_DISPATCHED"
        private const val EXTRA_MANUAL_SOS = "com.example.swiftaid.extra.MANUAL_SOS"
        private const val MONITOR_CHANNEL_ID = "swift_aid_monitor"
        private const val CRASH_CHANNEL_ID = "swift_aid_crash"
        private const val MONITOR_NOTIFICATION_ID = 1001
        private const val CRASH_NOTIFICATION_ID = 1002
        private const val SOS_OVERLAY_REQUEST_CODE = 2001
        private const val SENSOR_DELAY_US = 20_000
        private const val SENSOR_SAMPLE_MS = 20L
        private const val NATIVE_CRASH_ACCIDENT_LABEL_WINDOW_NS = 1_000_000_000L
        private const val MANUAL_SOS_ACCIDENT_LABEL_WINDOW_NS = 10_000_000_000L
        private const val MOVEMENT_ACCEL_THRESHOLD_MPS2 = 0.8f
        private const val MOVEMENT_GYRO_THRESHOLD_RAD_S = 0.15f
        private const val DRIVING_SPEED_THRESHOLD_MPS = 2.0f
        private const val STATIONARY_LOG_RESET_MS = 10_000L
        private const val LOCATION_SAMPLE_INTERVAL_MS = 1_000L
        private const val LOCATION_FASTEST_INTERVAL_MS = 500L
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

        fun notifySosDispatched(context: Context, manualSos: Boolean = false) {
            context.startService(
                Intent(context, CrashDetectionService::class.java).setAction(ACTION_SOS_DISPATCHED)
                    .putExtra(EXTRA_MANUAL_SOS, manualSos)
            )
        }
    }
}
