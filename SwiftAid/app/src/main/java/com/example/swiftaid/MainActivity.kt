package com.example.swiftaid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.swiftaid.databinding.ActivityMainBinding
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

        renderSelectedContacts()

        binding.pickContact.setOnClickListener {
            pickEmergencyContact()
        }

        binding.manualSos.setOnClickListener {
            startManualSosWhenReady()
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
        } else if (requestCode == REQUEST_PICK_CONTACT && resultCode == RESULT_OK) {
            val phoneContact = data?.data?.let { readPickedPhoneContact(it) }
            if (phoneContact == null) {
                binding.statusText.text = getString(R.string.main_status_contact_pick_failed)
            } else {
                addEmergencyContact(phoneContact.number)
            }
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
        } else if (requestCode == REQUEST_CONTACTS_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                openSystemContactPicker()
            } else {
                binding.statusText.text = getString(R.string.main_status_contact_pick_failed)
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

    private fun requestDefaultDialerRoleIfAvailable(): Boolean {
        return DefaultDialerRoleHelper.requestDefaultDialerRole(this, REQUEST_DEFAULT_DIALER_ROLE).also { requested ->
            if (!requested) {
                binding.statusText.text = getString(R.string.main_status_dialer_role_unavailable)
            }
        }
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

    private fun renderStatus() {
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

    private fun pickEmergencyContact() {
        if (EmergencySmsDispatcher.getEmergencyContacts(this).size >= MAX_EMERGENCY_CONTACTS) {
            binding.statusText.text = getString(R.string.main_status_contact_limit)
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS_PERMISSION)
            return
        }
        openSystemContactPicker()
    }

    private fun openSystemContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        runCatching {
            startActivityForResult(intent, REQUEST_PICK_CONTACT)
        }.onFailure {
            binding.statusText.text = getString(R.string.main_status_contact_pick_failed)
        }
    }

    private fun readPickedPhoneContact(contactUri: Uri): PhoneContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex < 0) return@use null

            val number = normalizePhoneNumber(cursor.getString(numberIndex).orEmpty())
            if (number.isBlank()) return@use null
            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty().trim() else ""
            PhoneContact(name = name.ifBlank { number }, number = number)
        }
    }

    private fun addEmergencyContact(phoneNumber: String) {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val contacts = EmergencySmsDispatcher.getEmergencyContacts(this)
            .map { normalizePhoneNumber(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()

        if (contacts.contains(normalizedNumber)) {
            binding.statusText.text = getString(R.string.main_status_contact_exists)
            return
        }
        if (contacts.size >= MAX_EMERGENCY_CONTACTS) {
            binding.statusText.text = getString(R.string.main_status_contact_limit)
            return
        }

        contacts.add(normalizedNumber)
        EmergencySmsDispatcher.saveEmergencyContacts(this, contacts.joinToString(separator = "\n"))
        renderSelectedContacts()
        binding.statusText.text = getString(R.string.main_status_contact_added)
    }

    private fun loadSavedPhoneContacts(): List<PhoneContact> {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val contactsByNumber = linkedMapOf<String, PhoneContact>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val rawNumber = cursor.getString(numberIndex)?.trim().orEmpty()
                val number = normalizePhoneNumber(rawNumber)
                if (number.isBlank()) continue
                val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { number }
                contactsByNumber.putIfAbsent(number, PhoneContact(name = name, number = number))
            }
        }
        return contactsByNumber.values.toList()
    }

    private fun renderSelectedContacts() {
        val contacts = EmergencySmsDispatcher.getEmergencyContacts(this).take(MAX_EMERGENCY_CONTACTS)
        if (contacts.size != EmergencySmsDispatcher.getEmergencyContacts(this).size) {
            EmergencySmsDispatcher.saveEmergencyContacts(this, contacts.joinToString(separator = "\n"))
        }
        val contactNames = loadSavedPhoneContacts().associateBy { it.number }
        binding.contactsLabel.text = getString(R.string.main_contacts_count, contacts.size)
        binding.selectedContacts.text = if (contacts.isEmpty()) {
            getString(R.string.main_contacts_empty)
        } else {
            contacts.mapIndexed { index, contact ->
                val savedContact = contactNames[normalizePhoneNumber(contact)]
                val label = savedContact?.let { "${it.name}\n${it.number}" } ?: contact
                "${index + 1}. $label"
            }
                .joinToString(separator = "\n")
        }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return PhoneNumberUtils.normalizeNumber(phoneNumber).ifBlank {
            phoneNumber.filter { it.isDigit() || it == '+' }
        }
    }

    private fun requiredPermissions(): List<String> {
        return buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.RECEIVE_SMS)
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
        }
    }

    companion object {
        private const val REQUEST_REQUIRED_PERMISSIONS = 4100
        private const val REQUEST_MANUAL_SOS_PERMISSIONS = 4101
        private const val REQUEST_LOCATION_SETTINGS = 4103
        private const val REQUEST_DEFAULT_DIALER_ROLE = 4104
        private const val REQUEST_CONTACTS_PERMISSION = 4105
        private const val REQUEST_PICK_CONTACT = 4106
        private const val MAX_EMERGENCY_CONTACTS = 5
        private const val GPS_CHECK_INTERVAL_MS = 1_000L
    }
}

private data class PhoneContact(
    val name: String,
    val number: String
) {
    val displayLabel: String = "$name\n$number"
}
