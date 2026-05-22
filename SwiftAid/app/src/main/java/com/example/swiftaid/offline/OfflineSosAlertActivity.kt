package com.example.swiftaid.offline

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.roundToInt

class OfflineSosAlertActivity : AppCompatActivity() {
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var speed: Double? = null
    private var sender: String? = null
    private var emergencyMatch: OfflineEmergencyMatch? = null

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OfflineSosAlertController.ACTION_DISMISS) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        parseIntent(intent)
        loadOfflineDirectoryMatch()
        buildUi()
        registerDismissReceiver()
        hideSystemBarsWhenReady()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
        loadOfflineDirectoryMatch()
        buildUi()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    private fun parseIntent(intent: Intent) {
        latitude = intent.getDoubleExtraOrNull(OfflineSosAlertController.EXTRA_LAT)
        longitude = intent.getDoubleExtraOrNull(OfflineSosAlertController.EXTRA_LNG)
        speed = intent.getDoubleExtraOrNull(OfflineSosAlertController.EXTRA_SPEED)
        sender = intent.getStringExtra(OfflineSosAlertController.EXTRA_SENDER)
    }

    private fun loadOfflineDirectoryMatch() {
        val lat = latitude
        val lng = longitude
        emergencyMatch = if (lat != null && lng != null) {
            runCatching { OfflineEmergencyDirectory(this).findNearest(lat, lng) }.getOrNull()
        } else {
            null
        }
    }

    private fun buildUi() {
        val scrollView = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(69, 16, 23), Color.rgb(16, 22, 29))
            )
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(48), dp(20), dp(28))
        }
        scrollView.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            TextView(this).apply {
                text = "SwiftAid SOS"
                setTextColor(Color.WHITE)
                textSize = 34f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
            },
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        root.addView(
            TextView(this).apply {
                text = "Crash alert received by SMS"
                setTextColor(Color.rgb(244, 203, 122))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(22))
            },
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val match = emergencyMatch
        val locationText = TextView(this).apply {
            text = buildLocationSummary(match)
            setTextColor(Color.rgb(229, 237, 241))
            textSize = 16f
            gravity = Gravity.START
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedDrawable(Color.argb(224, 29, 40, 48), Color.argb(96, 255, 255, 255))
        }
        root.addView(
            locationText,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        addMapsButton(root)
        addCallButton(root, "Call Nearest Hospital", match?.hospital)
        addCallButton(root, "Call Nearest Police", match?.police)

        root.addView(
            Button(this).apply {
                text = "Stop Alarm"
                isAllCaps = false
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedDrawable(Color.rgb(75, 84, 91))
                setOnClickListener { OfflineSosAlertController.dismiss(this@OfflineSosAlertActivity) }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                topMargin = dp(18)
            }
        )

        setContentView(scrollView)
    }

    private fun addMapsButton(root: LinearLayout) {
        val lat = latitude
        val lng = longitude
        if (lat == null || lng == null) return

        root.addView(
            Button(this).apply {
                text = "Open in Google Maps"
                isAllCaps = false
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedDrawable(Color.rgb(28, 127, 113))
                setOnClickListener {
                    OfflineSosAlarmPlayer.stop()
                    val mapsUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
                    startActivity(Intent(Intent.ACTION_VIEW, mapsUri))
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                topMargin = dp(16)
            }
        )
    }

    private fun addCallButton(
        root: LinearLayout,
        label: String,
        contact: OfflineEmergencyContact?
    ) {
        root.addView(
            Button(this).apply {
                text = if (contact == null) label else "$label\n${contact.name}"
                isAllCaps = false
                textSize = 16f
                setTextColor(if (contact == null) Color.rgb(150, 162, 169) else Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedDrawable(
                    if (contact == null) Color.rgb(48, 57, 64) else Color.rgb(186, 49, 55)
                )
                isEnabled = contact?.phone?.isNotBlank() == true
                setOnClickListener {
                    contact?.let {
                        OfflineSosAlarmPlayer.stop()
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it.phone, null)))
                    }
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)).apply {
                topMargin = dp(12)
            }
        )
    }

    private fun buildLocationSummary(match: OfflineEmergencyMatch?): String {
        val coordinateLine = if (latitude != null && longitude != null) {
            "Coordinates: ${String.format(Locale.US, "%.6f, %.6f", latitude, longitude)}"
        } else {
            "Coordinates: unavailable"
        }
        val speedLine = "Speed: ${String.format(Locale.US, "%.1f m/s", speed ?: 0.0)}"
        val senderLine = sender?.let { "Sender: $it" }
        val districtLine = match?.let { "Offline match: ${it.district}" }
        val hospitalLine = match?.hospital?.let { "Hospital: ${it.name} (${formatDistance(it.distanceMeters)})" }
        val policeLine = match?.police?.let { "Police: ${it.name} (${formatDistance(it.distanceMeters)})" }

        return listOfNotNull(coordinateLine, speedLine, senderLine, districtLine, hospitalLine, policeLine)
            .joinToString(separator = "\n")
    }

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1_000.0) {
            String.format(Locale.US, "%.1f km", distanceMeters / 1_000.0)
        } else {
            "${distanceMeters.roundToInt()} m"
        }
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
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    private fun registerDismissReceiver() {
        val filter = IntentFilter(OfflineSosAlertController.ACTION_DISMISS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(dismissReceiver, filter)
        }
    }

    private fun Intent.getDoubleExtraOrNull(name: String): Double? {
        return if (hasExtra(name)) getDoubleExtra(name, 0.0) else null
    }

    private fun roundedDrawable(fillColor: Int, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fillColor)
            strokeColor?.let { setStroke(dp(1), it) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
