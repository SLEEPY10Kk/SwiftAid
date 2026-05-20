package com.example.swiftaid

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.swiftaid.databinding.ActivityMainBinding
import com.example.swiftaid.mesh.MeshRelayService
import com.example.swiftaid.mesh.MeshVolunteerSettings
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var permissionPromptInFlight = false
    private var gpsPromptInFlight = false
    private var overlayPromptInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactInput.setText(
            getSharedPreferences(EmergencySmsDispatcher.PREFS_NAME, MODE_PRIVATE)
                .getString(EmergencySmsDispatcher.KEY_EMERGENCY_CONTACTS, "")
        )
        binding.volunteerMode.isChecked = MeshVolunteerSettings.isEnabled(this)
        binding.contactInput.doAfterTextChanged { editable ->
            EmergencySmsDispatcher.saveEmergencyContacts(this, editable?.toString().orEmpty())
        }

        binding.manualSos.setOnClickListener {
            EmergencySmsDispatcher.saveEmergencyContacts(this, binding.contactInput.text.toString())
            startManualSosWhenReady()
        }

        binding.volunteerMode.setOnCheckedChangeListener { _, enabled ->
            onVolunteerModeChanged(enabled)
        }

        renderStatus()
        ensureAutoMonitoring()
    }

    private fun showCrashOverlay() {
        val overlayIntent = Intent(this, SOSOverlayActivity::class.java)
            .setAction(SOSOverlayActivity.ACTION_MANUAL_SOS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(overlayIntent)
    }

    override fun onResume() {
        super.onResume()
        gpsPromptInFlight = false
        overlayPromptInFlight = false
        renderStatus()
        ensureAutoMonitoring()
    }

    @Deprecated("Deprecated in Android framework; used here for Google Play Services resolution callback.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LOCATION_SETTINGS) {
            gpsPromptInFlight = false
            ensureAutoMonitoring()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionPromptInFlight = false
        if (requestCode == REQUEST_REQUIRED_PERMISSIONS) {
            if (hasRequiredPermissions()) {
                ensureAutoMonitoring()
            } else {
                binding.statusText.text = getString(R.string.main_status_permissions_missing)
            }
        } else if (requestCode == REQUEST_MANUAL_SOS_PERMISSIONS) {
            if (hasManualSosPermissions()) {
                showCrashOverlay()
            } else {
                binding.statusText.text = getString(R.string.main_status_manual_permissions_missing)
            }
        } else if (requestCode == REQUEST_VOLUNTEER_PERMISSIONS) {
            if (hasNearbyPermissions()) {
                MeshVolunteerSettings.setEnabled(this, true)
                binding.volunteerMode.isChecked = true
                MeshRelayService.startPassive(this)
                binding.statusText.text = getString(R.string.main_status_volunteer_on)
            } else {
                MeshVolunteerSettings.setEnabled(this, false)
                binding.volunteerMode.isChecked = false
                binding.statusText.text = getString(R.string.main_status_volunteer_permissions_missing)
            }
        }
    }

    private fun ensureAutoMonitoring() {
        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            binding.statusText.text = getString(R.string.main_status_permissions_missing)
            if (!permissionPromptInFlight) {
                permissionPromptInFlight = true
                requestPermissions(missing.toTypedArray(), REQUEST_REQUIRED_PERMISSIONS)
            }
            return
        }

        binding.statusText.text = getString(R.string.main_status_auto_starting)
        promptForGpsThenOverlayAndStart()
    }

    private fun startMonitoring() {
        CrashDetectionService.start(this)
        binding.statusText.text = getString(R.string.main_status_monitoring)
    }

    private fun startManualSosWhenReady() {
        if (EmergencySmsDispatcher.getEmergencyContacts(this).isEmpty()) {
            binding.statusText.text = getString(R.string.main_status_contacts_missing)
            return
        }

        val missing = manualSosPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_MANUAL_SOS_PERMISSIONS)
        } else {
            showCrashOverlay()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun hasManualSosPermissions(): Boolean {
        return manualSosPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun hasNearbyPermissions(): Boolean {
        return nearbyPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun onVolunteerModeChanged(enabled: Boolean) {
        if (enabled) {
            val missing = nearbyPermissions().filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                binding.volunteerMode.isChecked = false
                requestPermissions(missing.toTypedArray(), REQUEST_VOLUNTEER_PERMISSIONS)
                return
            }

            MeshVolunteerSettings.setEnabled(this, true)
            MeshRelayService.startPassive(this)
            binding.statusText.text = getString(R.string.main_status_volunteer_on)
        } else {
            MeshVolunteerSettings.setEnabled(this, false)
            MeshRelayService.stop(this)
            binding.statusText.text = getString(R.string.main_status_volunteer_off)
        }
    }

    private fun renderStatus() {
        binding.volunteerMode.isChecked = MeshVolunteerSettings.isEnabled(this)
        binding.statusText.text = if (hasRequiredPermissions()) {
            getString(R.string.main_status_ready)
        } else {
            getString(R.string.main_status_permissions_missing)
        }
    }

    private fun promptForGpsThenOverlayAndStart() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_CHECK_INTERVAL_MS)
            .build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(this)
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                gpsPromptInFlight = false
                if (promptForOverlayIfNeeded()) {
                    startMonitoring()
                }
            }
            .addOnFailureListener { throwable ->
                binding.statusText.text = getString(R.string.main_status_gps_required)
                if (gpsPromptInFlight) return@addOnFailureListener
                gpsPromptInFlight = true
                if (throwable is ResolvableApiException) {
                    runCatching {
                        throwable.startResolutionForResult(this, REQUEST_LOCATION_SETTINGS)
                    }.onFailure {
                        openLocationSettings()
                    }
                } else {
                    openLocationSettings()
                }
            }
    }

    private fun promptForOverlayIfNeeded(): Boolean {
        if (Settings.canDrawOverlays(this)) {
            overlayPromptInFlight = false
            return true
        }

        binding.statusText.text = getString(R.string.main_status_overlay_required)
        if (!overlayPromptInFlight) {
            overlayPromptInFlight = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        return false
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun requiredPermissions(): List<String> {
        return buildList {
            add(Manifest.permission.RECORD_AUDIO)
            addAll(manualSosPermissions())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.distinct()
    }

    private fun manualSosPermissions(): List<String> {
        return buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            addAll(nearbyPermissions())
        }.distinct()
    }

    private fun nearbyPermissions(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }

    companion object {
        private const val REQUEST_REQUIRED_PERMISSIONS = 4100
        private const val REQUEST_MANUAL_SOS_PERMISSIONS = 4101
        private const val REQUEST_VOLUNTEER_PERMISSIONS = 4102
        private const val REQUEST_LOCATION_SETTINGS = 4103
        private const val GPS_CHECK_INTERVAL_MS = 1_000L
    }
}
