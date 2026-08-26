package com.example.swiftaid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.swiftaid.ui.theme.RoadSOSTheme
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var api: AuthApi
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var tokenStorage: TokenStorage

    private var runtimeState by mutableStateOf(SwiftAidRuntimeState())
    private var permissionPromptInFlight = false
    private var gpsPromptInFlight = false
    private var overlayPromptInFlight = false
    private var requiredPermissionPromptAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requiredPermissionPromptAttempted = savedInstanceState?.getBoolean(KEY_REQUIRED_PERMISSION_PROMPT_ATTEMPTED) ?: false

        val signInWithGoogle: ((AuthResult) -> Unit) -> Unit = { onResult ->
            lifecycleScope.launch { onResult(runGoogleAuth(false)) }
        }
        val signUpWithGoogle: ((AuthResult) -> Unit) -> Unit = { onResult ->
            lifecycleScope.launch { onResult(runGoogleAuth(true)) }
        }

        setContent {
            RoadSOSTheme {
                CompositionLocalProvider(
                    LocalSwiftAidRuntimeState provides runtimeState,
                    LocalSwiftAidRuntimeActions provides SwiftAidRuntimeActions(
                        startManualSos = ::startManualSosWhenReady,
                        manageEmergencyContacts = ::pickEmergencyContact,
                        refreshMonitoring = ::requestMonitoringSetup,
                        saveTypedEmergencyContact = ::saveTypedEmergencyContact
                    )
                ) {
                    App(
                        onGoogleSignInClick = signInWithGoogle,
                        onGoogleSignUpClick = signUpWithGoogle,
                        tokenStorage = tokenStorage,
                        api = api
                    )
                }
            }
        }

        refreshRuntimeState(statusMessage = getString(R.string.main_status_auto_starting))
        window.decorView.post {
            ensureAutoMonitoring(interactive = true)
        }
    }

    private suspend fun runGoogleAuth(register: Boolean): AuthResult {
        val credentialManager = CredentialManager.create(this)

        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            return AuthResult.Failure("Google OAuth is missing GOOGLE_WEB_CLIENT_ID in local.properties.")
        }
        return try {
            val signInWithGoogleRequest = buildGoogleCredentialRequest(useSignInWithGoogleOption = true)
            val oneTapRequest = buildGoogleCredentialRequest(useSignInWithGoogleOption = false)
            val result = try {
                credentialManager.getCredential(this, signInWithGoogleRequest)
            } catch (_: NoCredentialException) {
                credentialManager.getCredential(this, oneTapRequest)
            }
            val credential = result.credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                authRepository.completeGoogleAuth(idToken, register)
            } else {
                AuthResult.Failure("Google auth did not return an ID token.")
            }
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Failure("Google sign-in was cancelled.")
        } catch (exception: GetCredentialException) {
            Log.w("AUTH_DEBUG", "Google credential failure: ${exception.type} ${exception.message}", exception)
            AuthResult.Failure(
                "Google sign-in failed (${exception.type}): ${exception.message.orEmpty().ifBlank { "check SHA-1 in Firebase, Web client ID, and Play Services." }}"
            )
        } catch (throwable: Throwable) {
            Log.e("AUTH_DEBUG", "Google auth exception", throwable)
            AuthResult.Failure("Google auth failed: ${throwable.message.orEmpty().ifBlank { "unexpected error" }}")
        }
    }

    private fun buildGoogleCredentialRequest(useSignInWithGoogleOption: Boolean): GetCredentialRequest {
        val option = if (useSignInWithGoogleOption) {
            GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        } else {
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()
        }

        return GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
    }

    override fun onResume() {
        super.onResume()
        gpsPromptInFlight = false
        overlayPromptInFlight = false
        refreshRuntimeState()
        ensureAutoMonitoring(interactive = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_REQUIRED_PERMISSION_PROMPT_ATTEMPTED, requiredPermissionPromptAttempted)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android framework; used here for Google Play Services resolution callback.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LOCATION_SETTINGS) {
            gpsPromptInFlight = false
            ensureAutoMonitoring(interactive = true)
        }
    }

    @Deprecated("Deprecated in Android framework; used here for runtime permission callback compatibility.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionPromptInFlight = false
        when (requestCode) {
            REQUEST_REQUIRED_PERMISSIONS -> {
                if (hasRequiredPermissions()) {
                    ensureAutoMonitoring(interactive = true)
                } else {
                    refreshRuntimeState(statusMessage = getString(R.string.main_status_permissions_missing))
                }
            }
            REQUEST_MANUAL_SOS_PERMISSIONS -> {
                if (!hasManualSosPermissions()) {
                    refreshRuntimeState(statusMessage = getString(R.string.main_status_manual_permissions_missing))
                }
                showManualSosOverlay()
            }
            REQUEST_CONTACTS_PERMISSION -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    openContactSelection()
                } else {
                    refreshRuntimeState(statusMessage = getString(R.string.main_status_contact_pick_failed))
                }
            }
        }
    }

    private fun requestMonitoringSetup() {
        gpsPromptInFlight = false
        overlayPromptInFlight = false
        ensureAutoMonitoring(interactive = true, retryPermissions = true)
    }

    private fun ensureAutoMonitoring(
        interactive: Boolean = false,
        retryPermissions: Boolean = false
    ) {
        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            refreshRuntimeState(
                statusMessage = getString(R.string.main_status_permissions_missing),
                isMonitoring = false
            )
            val shouldRequestPermissions = interactive &&
                !permissionPromptInFlight &&
                (retryPermissions || !requiredPermissionPromptAttempted)
            if (shouldRequestPermissions) {
                requiredPermissionPromptAttempted = true
                permissionPromptInFlight = true
                requestPermissions(missing.toTypedArray(), REQUEST_REQUIRED_PERMISSIONS)
            }
            return
        }

        refreshRuntimeState(statusMessage = getString(R.string.main_status_auto_starting))
        promptForGpsThenOverlayAndStart(interactive)
    }

    private fun promptForGpsThenOverlayAndStart(interactive: Boolean) {
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
                if (promptForOverlayIfNeeded(interactive)) {
                    startMonitoring()
                }
            }
            .addOnFailureListener { throwable ->
                refreshRuntimeState(
                    statusMessage = getString(R.string.main_status_gps_required),
                    isMonitoring = false
                )
                if (!interactive || gpsPromptInFlight) return@addOnFailureListener
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

    private fun promptForOverlayIfNeeded(interactive: Boolean): Boolean {
        if (Settings.canDrawOverlays(this)) {
            overlayPromptInFlight = false
            return true
        }

        refreshRuntimeState(
            statusMessage = getString(R.string.main_status_overlay_required),
            isMonitoring = false
        )
        if (interactive && !overlayPromptInFlight) {
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

    private fun startMonitoring() {
        CrashDetectionService.start(this)
        refreshRuntimeState(
            statusMessage = getString(R.string.main_status_monitoring),
            isMonitoring = true
        )
    }

    private fun startManualSosWhenReady() {
        val missing = manualSosPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            if (!permissionPromptInFlight) {
                permissionPromptInFlight = true
                requestPermissions(missing.toTypedArray(), REQUEST_MANUAL_SOS_PERMISSIONS)
            }
        } else {
            showManualSosOverlay()
        }
    }

    private fun showManualSosOverlay() {
        val overlayIntent = Intent(this, SOSOverlayActivity::class.java)
            .setAction(SOSOverlayActivity.ACTION_MANUAL_SOS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(overlayIntent)
    }

    private fun pickEmergencyContact() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionPromptInFlight) {
                permissionPromptInFlight = true
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS_PERMISSION)
            }
            return
        }
        openContactSelection()
    }

    private fun openContactSelection() {
        startActivity(Intent(this, ContactSelectionActivity::class.java))
    }

    private fun saveTypedEmergencyContact(rawPhoneNumber: String) {
        val number = normalizePhoneNumber(rawPhoneNumber)
        if (number.isBlank()) {
            refreshRuntimeState(statusMessage = getString(R.string.main_status_contact_pick_failed))
            return
        }

        val updatedContacts = (EmergencySmsDispatcher.getEmergencyContacts(this) + number)
            .map { normalizePhoneNumber(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_EMERGENCY_CONTACTS)
        EmergencySmsDispatcher.saveEmergencyContacts(this, updatedContacts.joinToString(separator = "\n"))
        refreshRuntimeState(statusMessage = getString(R.string.main_status_contacts_updated))
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun hasManualSosPermissions(): Boolean {
        return manualSosPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun refreshRuntimeState(
        statusMessage: String = if (hasRequiredPermissions()) {
            getString(R.string.main_status_ready)
        } else {
            getString(R.string.main_status_permissions_missing)
        },
        isMonitoring: Boolean = runtimeState.isMonitoring
    ) {
        val contacts = EmergencySmsDispatcher.getEmergencyContacts(this).take(MAX_EMERGENCY_CONTACTS)
        if (contacts.size != EmergencySmsDispatcher.getEmergencyContacts(this).size) {
            EmergencySmsDispatcher.saveEmergencyContacts(this, contacts.joinToString(separator = "\n"))
        }

        runtimeState = SwiftAidRuntimeState(
            statusMessage = statusMessage,
            emergencyContacts = contacts,
            isMonitoring = isMonitoring,
            requiredPermissionsReady = hasRequiredPermissions()
        )
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

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return PhoneNumberUtils.normalizeNumber(phoneNumber).ifBlank {
            phoneNumber.filter { it.isDigit() || it == '+' }
        }
    }

    companion object {
        private const val REQUEST_REQUIRED_PERMISSIONS = 4100
        private const val REQUEST_MANUAL_SOS_PERMISSIONS = 4101
        private const val REQUEST_LOCATION_SETTINGS = 4103
        private const val REQUEST_CONTACTS_PERMISSION = 4105
        private const val MAX_EMERGENCY_CONTACTS = 5
        private const val GPS_CHECK_INTERVAL_MS = 1_000L
        private const val KEY_REQUIRED_PERMISSION_PROMPT_ATTEMPTED =
            "required_permission_prompt_attempted"
    }
}
