package com.example.swiftaid

import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

class SOSOverlayActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val sirenPlayer = SirenPlayer()
    private var secondsRemaining = COUNTDOWN_SECONDS
    private var completed = false
    private var previousMediaVolume: Int? = null

    private lateinit var countdownText: TextView
    private lateinit var statusText: TextView
    private lateinit var slider: SeekBar
    private lateinit var progressBar: ProgressBar

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (completed) return
            secondsRemaining -= 1
            if (secondsRemaining <= 0) {
                executeSos()
            } else {
                renderCountdown()
                handler.postDelayed(this, ONE_SECOND_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            }
        )
        buildUi()
        hideSystemBarsWhenReady()
        maximizeMediaVolume()
        sirenPlayer.start()
        renderCountdown()
        handler.postDelayed(countdownRunnable, ONE_SECOND_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sirenPlayer.stop()
        restoreMediaVolume()
        super.onDestroy()
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBarsWhenReady() {
        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.windowInsetsController?.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    private fun applyLegacySystemUiFlags() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsWhenReady()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(34))
            setBackgroundColor(Color.rgb(18, 22, 26))
        }

        val title = TextView(this).apply {
            text = getString(R.string.sos_title)
            setTextColor(Color.WHITE)
            textSize = 32f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val subtitle = TextView(this).apply {
            text = getString(R.string.sos_subtitle)
            setTextColor(Color.rgb(210, 218, 226))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(subtitle, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        root.addView(
            Space(this),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        countdownText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 76f
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(countdownText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        statusText = TextView(this).apply {
            text = getString(R.string.sos_status_counting)
            setTextColor(Color.rgb(244, 188, 100))
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(statusText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        root.addView(progressBar, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        root.addView(
            Space(this),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val sliderLabel = TextView(this).apply {
            text = getString(R.string.sos_slider_label)
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(sliderLabel, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        slider = SeekBar(this).apply {
            max = 100
            progress = 0
            progressDrawable = AppCompatResources.getDrawable(
                this@SOSOverlayActivity,
                R.drawable.sos_slider_track
            )
            thumb = AppCompatResources.getDrawable(this@SOSOverlayActivity, R.drawable.sos_slider_thumb)
            splitTrack = false
            setPadding(dp(4), dp(18), dp(4), dp(18))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && progress >= SWIPE_COMPLETE_PROGRESS) {
                        cancelSos()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    if (!completed && seekBar.progress < SWIPE_COMPLETE_PROGRESS) {
                        ObjectAnimator.ofInt(seekBar, "progress", 0).setDuration(180L).start()
                    }
                }
            })
        }
        root.addView(slider, LinearLayout.LayoutParams.MATCH_PARENT, dp(80))

        setContentView(root)
    }

    private fun renderCountdown() {
        countdownText.text = getString(R.string.sos_countdown_seconds, secondsRemaining)
    }

    private fun cancelSos() {
        if (completed) return
        completed = true
        handler.removeCallbacks(countdownRunnable)
        slider.isEnabled = false
        slider.progress = 100
        statusText.text = getString(R.string.sos_status_cancelled)
        NativeCrashBridge.resetEngine()
        CrashDetectionService.notifyCrashCancelled(this)
        handler.postDelayed({ finish() }, CANCEL_CLOSE_DELAY_MS)
    }

    private fun executeSos() {
        if (completed) return
        completed = true
        slider.isEnabled = false
        progressBar.visibility = View.VISIBLE
        countdownText.text = getString(R.string.sos_countdown_seconds, 0)
        statusText.text = getString(R.string.sos_status_location)

        LocationFallbackCascade(this).fetch { locationResult ->
            val result = EmergencySmsDispatcher.dispatch(this, locationResult)
            statusText.text = if (result.sentParts > 0) {
                resources.getQuantityString(
                    R.plurals.sos_status_sent,
                    result.attemptedContacts,
                    result.attemptedContacts
                )
            } else {
                getString(R.string.sos_status_not_sent)
            }
            progressBar.visibility = View.GONE
            handler.postDelayed({ finish() }, SENT_CLOSE_DELAY_MS)
        }
    }

    private fun maximizeMediaVolume() {
        val audioManager = getSystemService(AudioManager::class.java)
        runCatching {
            previousMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
            )
        }
    }

    private fun restoreMediaVolume() {
        val volume = previousMediaVolume ?: return
        runCatching {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_CRASH_CONFIRMED = "com.example.swiftaid.action.CRASH_CONFIRMED"
        private const val COUNTDOWN_SECONDS = 7
        private const val ONE_SECOND_MS = 1_000L
        private const val SWIPE_COMPLETE_PROGRESS = 94
        private const val CANCEL_CLOSE_DELAY_MS = 700L
        private const val SENT_CLOSE_DELAY_MS = 3_000L
    }
}

private class SirenPlayer {
    @Volatile
    private var running = false
    private var audioTrack: AudioTrack? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = thread(name = "SwiftAidSiren", isDaemon = true) {
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

                val buffer = ShortArray(minBuffer / 2)
                var phase = 0.0
                var sampleIndex = 0L
                while (running) {
                    for (i in buffer.indices) {
                        val sweep = (sampleIndex % SWEEP_SAMPLES).toDouble() / SWEEP_SAMPLES.toDouble()
                        val frequency = if (sweep < 0.5) {
                            650.0 + 900.0 * (sweep * 2.0)
                        } else {
                            1550.0 - 900.0 * ((sweep - 0.5) * 2.0)
                        }
                        phase += 2.0 * PI * frequency / SAMPLE_RATE
                        buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.35).toInt().toShort()
                        sampleIndex += 1
                    }
                    track.write(buffer, 0, buffer.size)
                }
            }.onFailure { throwable ->
                Log.w(TAG, "Unable to play SOS siren", throwable)
                running = false
            }
        }
    }

    fun stop() {
        running = false
        audioTrack?.runCatching {
            pause()
            flush()
            release()
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "SirenPlayer"
        private const val SAMPLE_RATE = 22_050
        private const val SWEEP_SECONDS = 2
        private const val SWEEP_SAMPLES = SAMPLE_RATE * SWEEP_SECONDS
    }
}
