package com.example.swiftaid

import android.app.KeyguardManager
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.swiftaid.mesh.MeshRelayService
import com.example.swiftaid.mesh.MeshSosPacket
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

class SOSOverlayActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val sirenPlayer = SirenPlayer()
    private var secondsRemaining = COUNTDOWN_SECONDS
    private var completed = false
    private var previousMediaVolume: Int? = null
    private var manualSos = false

    private lateinit var countdownText: TextView
    private lateinit var statusText: TextView
    private lateinit var emergencySlider: EmergencySliderView
    private lateinit var stopButton: TextView
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
        manualSos = intent?.action == ACTION_MANUAL_SOS
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
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(9, 17, 23))
        }
        root.addView(
            SosBackdropView(this),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(72), dp(24), dp(32))
        }
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val title = TextView(this).apply {
            text = getString(R.string.sos_title)
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        content.addView(title, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        countdownText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 56f
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = circleDrawable(Color.rgb(255, 22, 22))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        content.addView(
            countdownText,
            LinearLayout.LayoutParams(dp(110), dp(110)).apply {
                topMargin = dp(50)
            }
        )

        statusText = TextView(this).apply {
            text = getString(R.string.sos_status_counting)
            setTextColor(Color.rgb(244, 247, 248))
            textSize = 19f
            gravity = Gravity.CENTER
            maxWidth = dp(330)
            includeFontPadding = true
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        content.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(42)
            }
        )

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        }
        content.addView(
            progressBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) }
        )

        content.addView(
            Space(this),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        emergencySlider = EmergencySliderView(this).apply {
            label = getString(R.string.sos_slider_label)
            onCompleted = { executeSos() }
            contentDescription = getString(R.string.sos_slider_label)
        }
        val controlWidth = minOf(resources.displayMetrics.widthPixels - dp(64), dp(288))
        content.addView(
            emergencySlider,
            LinearLayout.LayoutParams(controlWidth, dp(68))
        )

        val stopGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        stopButton = TextView(this).apply {
            text = "\u00D7"
            setTextColor(Color.WHITE)
            textSize = 44f
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = circleDrawable(Color.argb(176, 176, 185, 171))
            contentDescription = getString(R.string.sos_stop_label)
            isClickable = true
            isFocusable = true
            setOnClickListener { cancelSos() }
        }
        stopGroup.addView(stopButton, LinearLayout.LayoutParams(dp(68), dp(68)))

        val stopLabel = TextView(this).apply {
            text = getString(R.string.sos_stop_label)
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
        }
        stopGroup.addView(
            stopLabel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )
        content.addView(
            stopGroup,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(36) }
        )

        setContentView(root)
    }

    private fun renderCountdown() {
        countdownText.text = getString(R.string.sos_countdown_seconds, secondsRemaining)
    }

    private fun cancelSos() {
        if (completed) return
        completed = true
        handler.removeCallbacks(countdownRunnable)
        emergencySlider.isEnabled = false
        emergencySlider.alpha = 0.55f
        stopButton.isEnabled = false
        stopButton.alpha = 0.55f
        statusText.text = getString(R.string.sos_status_cancelled)
        NativeCrashBridge.resetEngine()
        CrashDetectionService.notifyCrashCancelled(this)
        handler.postDelayed({ finish() }, CANCEL_CLOSE_DELAY_MS)
    }

    private fun executeSos() {
        if (completed) return
        completed = true
        handler.removeCallbacks(countdownRunnable)
        emergencySlider.isEnabled = false
        emergencySlider.alpha = 0.55f
        stopButton.isEnabled = false
        stopButton.alpha = 0.55f
        progressBar.visibility = View.VISIBLE
        countdownText.text = getString(R.string.sos_countdown_seconds, 0)
        statusText.text = getString(R.string.sos_status_location)
        CrashDetectionService.notifySosDispatched(this, manualSos = manualSos)

        LocationFallbackCascade(this).fetch { locationResult ->
            MeshRelayService.broadcastLocalSos(
                this,
                MeshSosPacket.fromLocationResult(this, locationResult)
            )
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

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_CRASH_CONFIRMED = "com.example.swiftaid.action.CRASH_CONFIRMED"
        const val ACTION_MANUAL_SOS = "com.example.swiftaid.action.MANUAL_SOS"
        private const val COUNTDOWN_SECONDS = 7
        private const val ONE_SECOND_MS = 1_000L
        private const val CANCEL_CLOSE_DELAY_MS = 700L
        private const val SENT_CLOSE_DELAY_MS = 3_000L
    }
}

private class SosBackdropView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            intArrayOf(
                Color.rgb(4, 67, 101),
                Color.rgb(7, 65, 52),
                Color.rgb(27, 43, 98),
                Color.rgb(78, 47, 23)
            ),
            floatArrayOf(0f, 0.36f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        drawGlow(canvas, w * 0.2f, h * 0.31f, w * 0.5f, Color.rgb(184, 128, 38), 108)
        drawGlow(canvas, w * 0.78f, h * 0.42f, w * 0.68f, Color.rgb(37, 68, 164), 136)
        drawGlow(canvas, w * 0.32f, h * 0.78f, w * 0.78f, Color.rgb(146, 144, 13), 126)
        drawGlow(canvas, w * 0.04f, h * 0.72f, w * 0.55f, Color.rgb(0, 129, 98), 118)
        drawGlow(canvas, w * 0.96f, h * 0.7f, w * 0.62f, Color.rgb(122, 72, 24), 96)

        paint.shader = null
        paint.color = Color.argb(82, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                withAlpha(color, alpha),
                withAlpha(color, alpha / 3),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}

private class EmergencySliderView(context: Context) : View(context) {
    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var onCompleted: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val trackRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(16f)
    }
    private val sosPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(19f)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(54, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private var thumbRadius = 0f
    private var progress = 0f
    private var tracking = false
    private var resetAnimator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(288), widthMeasureSpec),
            resolveSize(dp(68), heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTrackBounds(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackRect.isEmpty) {
            updateTrackBounds(width, height)
        }

        val trackRadius = trackRect.height() / 2f
        trackPaint.shader = LinearGradient(
            trackRect.left,
            trackRect.top,
            trackRect.right,
            trackRect.bottom,
            intArrayOf(
                Color.argb(144, 238, 238, 229),
                Color.argb(108, 194, 199, 204)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)
        trackPaint.shader = null

        val labelCenterX = trackRect.left + (thumbRadius * 2f) + ((trackRect.width() - thumbRadius * 2f) / 2f)
        val labelBaseline = trackRect.centerY() - ((labelPaint.descent() + labelPaint.ascent()) / 2f)
        canvas.drawText(label, labelCenterX, labelBaseline, labelPaint)

        val thumbCx = thumbCenterX()
        val thumbCy = trackRect.centerY()
        thumbPaint.color = Color.rgb(255, 73, 67)
        thumbPaint.style = Paint.Style.FILL
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius - density, thumbRingPaint)

        val sosBaseline = thumbCy - ((sosPaint.descent() + sosPaint.ascent()) / 2f)
        canvas.drawText("SOS", thumbCx, sosBaseline, sosPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchOnThumb(event.x, event.y)) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                resetAnimator?.cancel()
                tracking = true
                updateProgress(event.x)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                updateProgress(event.x)
                true
            }

            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                parent?.requestDisallowInterceptTouchEvent(false)
                updateProgress(event.x)
                if (progress >= COMPLETE_THRESHOLD) {
                    progress = 1f
                    invalidate()
                    performClick()
                    onCompleted?.invoke()
                } else {
                    animateProgressToStart()
                    performClick()
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                parent?.requestDisallowInterceptTouchEvent(false)
                animateProgressToStart()
                true
            }

            else -> super.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        resetAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun updateTrackBounds(w: Int, h: Int) {
        val trackHeight = minOf(h.toFloat(), 64f * density)
        val top = (h - trackHeight) / 2f
        trackRect.set(0f, top, w.toFloat(), top + trackHeight)
        thumbRadius = (trackHeight / 2f) - (4f * density)
    }

    private fun isTouchOnThumb(x: Float, y: Float): Boolean {
        val dx = x - thumbCenterX()
        val dy = y - trackRect.centerY()
        val touchRadius = thumbRadius * 1.45f
        return dx * dx + dy * dy <= touchRadius * touchRadius
    }

    private fun updateProgress(x: Float) {
        val travel = trackRect.width() - (thumbRadius * 2f)
        if (travel <= 0f) return
        progress = ((x - trackRect.left - thumbRadius) / travel).coerceIn(0f, 1f)
        invalidate()
    }

    private fun animateProgressToStart() {
        resetAnimator?.cancel()
        resetAnimator = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 180L
            addUpdateListener { animator ->
                progress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun thumbCenterX(): Float =
        trackRect.left + thumbRadius + ((trackRect.width() - thumbRadius * 2f) * progress)

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        private const val COMPLETE_THRESHOLD = 0.86f
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
