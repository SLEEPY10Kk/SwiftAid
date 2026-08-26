package com.example.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

private val SettingsBlue = Color(0xFF3B82F6)
private val SettingsBlueDark = Color(0xFF4A7FF5)
private val SettingsBgLight = Color(0xFFF5F7FB)
private val SettingsBgDark = Color(0xFF0F172A)
private val SettingsCardLight = Color.White
private val SettingsCardDark = Color(0xFF162033)
private val SettingsMuted = Color(0xFF718096)
private val SettingsDanger = Color(0xFFE74C3C)

@Composable
fun SettingsScreenV2(
    api: AuthApi? = null,
    tokenStorage: TokenStorage? = null,
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    val sharedState = LocalSharedState.current
    val isDark = LocalIsDark.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val repository = remember(api) { api?.let { SettingsRepository(it) } }

    var activeEditor by remember { mutableStateOf<SettingsEditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var pendingPhoneUpdate by remember { mutableStateOf<String?>(null) }
    var phoneOtpDebugCode by remember { mutableStateOf<String?>(null) }
    var phoneOtpError by remember { mutableStateOf<String?>(null) }
    var phoneOtpSending by remember { mutableStateOf(false) }
    var phoneOtpVerifying by remember { mutableStateOf(false) }

    suspend fun refreshSession(): Boolean {
        val currentApi = api ?: return false
        val refreshToken = sharedState.refreshToken.ifBlank { tokenStorage?.getRefreshToken().orEmpty() }
        if (refreshToken.isBlank()) return false
        val response = currentApi.refreshTokens(refreshToken)
        if (response.status != HttpStatusCode.OK) return false
        val body = response.body<AuthResponse>()
        sharedState.accessToken = body.accessToken
        sharedState.refreshToken = body.refreshToken
        sharedState.userId = extractUserIdFromJwt(body.accessToken)
        tokenStorage?.saveTokens(body.accessToken, body.refreshToken)
        return true
    }

    suspend fun <T> retryOnce(block: suspend () -> T): T {
        return runCatching { block() }.getOrElse { first ->
            if (refreshSession()) {
                block()
            } else {
                throw first
            }
        }
    }

    fun applySettingsSnapshotAndCacheEmergencyContacts(snapshot: SettingsSnapshot) {
        sharedState.applySettingsSnapshot(snapshot)
        EmergencySmsDispatcher.saveEmergencyContacts(
            context,
            snapshot.emergencyContacts
                .map { it.contactNumber }
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n")
        )
    }

    suspend fun reloadSettings() {
        val repo = repository ?: return
        sharedState.settingsLoading = true
        runCatching {
            retryOnce { repo.load(sharedState.accessToken) }
        }.onSuccess { snapshot ->
            applySettingsSnapshotAndCacheEmergencyContacts(snapshot)
            sharedState.userId = snapshot.user?.id ?: sharedState.userId
        }.onFailure { error ->
            sharedState.settingsLoading = false
            sharedState.settingsError = error.message ?: "Could not load settings"
        }
    }

    suspend fun sendPhoneOtp(phoneNumber: String): PhoneOtpResponse? {
        val currentApi = api ?: return null
        if (sharedState.accessToken.isBlank()) return null
        var response: PhoneOtpResponse? = null
        runCatching {
            val httpResponse = currentApi.sendPhoneOtp(sharedState.accessToken, phoneNumber)
            if (httpResponse.status == HttpStatusCode.OK) {
                response = httpResponse.body()
            } else if (httpResponse.status == HttpStatusCode.Conflict) {
                throw IllegalStateException("PHONE_TAKEN")
            } else {
                throw IllegalStateException(runCatching { httpResponse.body<ErrorResponse>().detail }.getOrDefault("Could not send verification code."))
            }
        }.recoverCatching {
            if (refreshSession()) {
                val httpResponse = currentApi.sendPhoneOtp(sharedState.accessToken, phoneNumber)
                if (httpResponse.status == HttpStatusCode.OK) {
                    response = httpResponse.body()
                } else if (httpResponse.status == HttpStatusCode.Conflict) {
                    throw IllegalStateException("PHONE_TAKEN")
                } else {
                    throw IllegalStateException(runCatching { httpResponse.body<ErrorResponse>().detail }.getOrDefault("Could not send verification code."))
                }
            } else {
                throw it
            }
        }
        return response
    }

    suspend fun verifyPhoneOtp(phoneNumber: String, otpCode: String): PhoneOtpResponse? {
        val currentApi = api ?: return null
        if (sharedState.accessToken.isBlank()) return null
        var response: PhoneOtpResponse? = null
        runCatching {
            val httpResponse = currentApi.verifyPhoneOtp(sharedState.accessToken, phoneNumber, otpCode)
            if (httpResponse.status == HttpStatusCode.OK) {
                response = httpResponse.body()
            } else {
                throw IllegalStateException(runCatching { httpResponse.body<ErrorResponse>().detail }.getOrDefault("Verification failed."))
            }
        }.recoverCatching {
            if (refreshSession()) {
                val httpResponse = currentApi.verifyPhoneOtp(sharedState.accessToken, phoneNumber, otpCode)
                if (httpResponse.status == HttpStatusCode.OK) {
                    response = httpResponse.body()
                } else {
                    throw IllegalStateException(runCatching { httpResponse.body<ErrorResponse>().detail }.getOrDefault("Verification failed."))
                }
            } else {
                throw it
            }
        }
        return response
    }

    LaunchedEffect(sharedState.userId) {
        if (sharedState.userId != null && repository != null) {
            reloadSettings()
        }
    }

    val snapshot = sharedState.settingsSnapshot
    val background = if (isDark) {
        Brush.verticalGradient(listOf(SettingsBgDark, Color(0xFF08111F)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFEAF1FF), SettingsBgLight))
    }

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(bottom = 28.dp)
            ) {
                SettingsTopBar(onBack = onBack, isDark = isDark)

                ProfileSummaryCard(
                    name = snapshot?.user?.fullName?.takeIf { it.isNotBlank() }
                        ?: listOfNotNull(snapshot?.user?.firstName, snapshot?.user?.lastName).joinToString(" ").trim().ifBlank {
                            snapshot?.user?.username ?: "User"
                        },
                    phone = snapshot?.user?.phoneNumber.orEmpty(),
                    percent = sharedState.safetyProfilePercent.coerceIn(0, 100),
                    isDark = isDark,
                    onEditProfile = onProfileClick
                )

                if (sharedState.settingsLoading) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = SettingsBlueDark)
                    }
                }

                sharedState.settingsError.takeIf { it.isNotBlank() }?.let { error ->
                    Spacer(modifier = Modifier.height(18.dp))
                    SettingsMessageCard(
                        icon = Icons.Default.ErrorOutline,
                        title = "Load failed",
                        message = error,
                        isDark = isDark,
                        accent = SettingsDanger
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle("Basic profile", "Edit the core account data stored on the backend", isDark)
                SettingsSectionCard(isDark) {
                    BasicProfileField.values().forEachIndexed { index, field ->
                        EditableFieldRow(
                            label = field.label,
                            value = snapshot?.basicFieldValue(field).orEmpty(),
                            isDark = isDark,
                            onEdit = {
                                activeEditor = SettingsEditorState.BasicFieldEditor(
                                    field = field,
                                    currentValue = snapshot?.basicFieldValue(field).orEmpty()
                                )
                            }
                        )
                        if (index != BasicProfileField.values().lastIndex) DividerRow(isDark)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle("Safety data", "Emergency contact, medical, and insurance records", isDark)

                SettingsSectionCard(isDark) {
                    Text(
                        text = "Emergency contacts",
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (snapshot?.emergencyContacts.isNullOrEmpty()) {
                        MissingFieldCard(
                            label = "Add emergency contact",
                            isDark = isDark,
                            onClick = {
                                activeEditor = SettingsEditorState.EmergencyContactEditor()
                            }
                        )
                    } else {
                        snapshot?.emergencyContacts.orEmpty().forEachIndexed { index, contact ->
                            EmergencyContactCard(
                                contact = contact,
                                isDark = isDark,
                                onEdit = { activeEditor = SettingsEditorState.EmergencyContactEditor(contact) },
                                onDelete = { deleteTarget = DeleteTarget.Emergency(contact) }
                            )
                            if (index != snapshot.emergencyContacts.lastIndex) Spacer(modifier = Modifier.height(10.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { activeEditor = SettingsEditorState.EmergencyContactEditor() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Text(text = "Add emergency contact", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SettingsSectionCard(isDark) {
                    Text(
                        text = "Medical info",
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MedicalField.values().forEachIndexed { index, field ->
                        EditableFieldRow(
                            label = field.label,
                            value = snapshot?.medicalFieldValue(field).orEmpty(),
                            isDark = isDark,
                            onEdit = {
                                activeEditor = SettingsEditorState.MedicalFieldEditor(
                                    field = field,
                                    currentValue = snapshot?.medicalFieldValue(field).orEmpty()
                                )
                            }
                        )
                        if (index != MedicalField.values().lastIndex) DividerRow(isDark)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SettingsSectionCard(isDark) {
                    Text(
                        text = "Insurance info",
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val insuranceRecords = snapshot?.insuranceInfo.orEmpty()
                    if (insuranceRecords.isEmpty()) {
                        MissingFieldCard(
                            label = "Add insurance info",
                            isDark = isDark,
                            onClick = { activeEditor = SettingsEditorState.InsuranceEditor() }
                        )
                    } else {
                        insuranceRecords.forEachIndexed { index, insurance ->
                            InsuranceRecordCard(
                                insurance = insurance,
                                isDark = isDark,
                                onEdit = { activeEditor = SettingsEditorState.InsuranceEditor(insurance) },
                                onDelete = { deleteTarget = DeleteTarget.Insurance(insurance) }
                            )
                            if (index != insuranceRecords.lastIndex) Spacer(modifier = Modifier.height(10.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { activeEditor = SettingsEditorState.InsuranceEditor() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Text(text = "Add insurance policy", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle("Support", "Session actions and app information", isDark)
                SettingsSectionCard(isDark) {
                    ActionRow(
                        icon = Icons.Default.Shield,
                        title = "Privacy and policy",
                        subtitle = "Review app policy text",
                        onClick = { TermsState.isVisible = true },
                        isDark = isDark
                    )
                    DividerRow(isDark)
                    ActionRow(
                        icon = Icons.Default.Person,
                        title = "Complete User Profile",
                        subtitle = "View the full organized profile",
                        onClick = onProfileClick,
                        isDark = isDark
                    )
                    DividerRow(isDark)
                    DestructiveRow(
                        icon = Icons.Default.Delete,
                        title = "Sign out",
                        subtitle = "Clear the current session",
                        onClick = onSignOut,
                        isDark = isDark
                    )
                }
            }
        }
    }

    activeEditor?.let { editor ->
        SettingsEditorSheet(
            editor = editor,
            onDismiss = { activeEditor = null },
            onSaveBasicField = { field, value ->
                val repo = repository
                if (repo != null) {
                    scope.launch {
                        if (field == BasicProfileField.Phone) {
                            sharedState.settingsLoading = true
                            phoneOtpError = null
                            phoneOtpDebugCode = null
                            phoneOtpSending = true
                            try {
                                val otpResponse = sendPhoneOtp(value)
                                if (otpResponse != null) {
                                    pendingPhoneUpdate = value
                                    phoneOtpDebugCode = otpResponse.debugCode
                                    activeEditor = null
                                } else {
                                    sharedState.settingsLoading = false
                                    onMessage("Could not send verification code.")
                                }
                            } catch (e: Exception) {
                                sharedState.settingsLoading = false
                                if (e.message == "PHONE_TAKEN") {
                                    onMessage("NUMBER ALREADY TAKEN, TRY A DIFFERENT ONE")
                                } else {
                                    onMessage(e.message ?: "Could not send verification code.")
                                }
                            } finally {
                                phoneOtpSending = false
                            }
                            return@launch
                        }

                        sharedState.settingsLoading = true
                        val request = when (field) {
                            BasicProfileField.Username -> UserUpdateRequest(username = value)
                            BasicProfileField.FullName -> UserUpdateRequest(fullName = value)
                            BasicProfileField.Phone -> UserUpdateRequest(phoneNumber = value)
                            BasicProfileField.Country -> UserUpdateRequest(country = value)
                            BasicProfileField.State -> UserUpdateRequest(state = value)
                            BasicProfileField.City -> UserUpdateRequest(city = value)
                            BasicProfileField.Area -> UserUpdateRequest(area = value)
                        }
                        runCatching { retryOnce { repo.saveUser(sharedState.accessToken, request) } }
                            .onSuccess { applySettingsSnapshotAndCacheEmergencyContacts(it) }
                            .onFailure {
                                sharedState.settingsLoading = false
                                sharedState.settingsError = it.message ?: "Could not save field"
                            }
                        activeEditor = null
                    }
                }
            },
            onSaveMedicalField = { field, value ->
                val repo = repository
                if (repo != null) {
                    scope.launch {
                        sharedState.settingsLoading = true
                        val request = when (field) {
                            MedicalField.BloodGroup -> MedicalInfoUpdateRequest(bloodGroup = value)
                            MedicalField.Allergies -> MedicalInfoUpdateRequest(allergies = value)
                            MedicalField.ChronicConditions -> MedicalInfoUpdateRequest(chronicConditions = value)
                            MedicalField.CurrentMedications -> MedicalInfoUpdateRequest(currentMedications = value)
                        }
                        runCatching { retryOnce { repo.updateMedicalInfo(sharedState.accessToken, request) } }
                            .onSuccess { applySettingsSnapshotAndCacheEmergencyContacts(it) }
                            .onFailure {
                                sharedState.settingsLoading = false
                                sharedState.settingsError = it.message ?: "Could not save field"
                            }
                        activeEditor = null
                    }
                }
            },
            onSaveEmergencyContact = { existing, contactName, contactNumber, relationship, priority ->
                val repo = repository
                if (repo != null) {
                    scope.launch {
                        sharedState.settingsLoading = true
                        val result = runCatching {
                            if (existing == null) {
                                retryOnce {
                                    repo.createEmergencyContact(
                                        sharedState.accessToken,
                                    EmergencyContactRequest(
                                        contactName = contactName.ifBlank { null },
                                        contactNumber = contactNumber,
                                        relationship = relationship,
                                        priorityOrder = priority
                                    )
                                )
                                }
                            } else {
                                retryOnce {
                                    repo.updateEmergencyContact(
                                        sharedState.accessToken,
                                    existing.contactId,
                                    EmergencyContactUpdateRequest(
                                        contactName = contactName.ifBlank { null },
                                        contactNumber = contactNumber,
                                        relationship = relationship,
                                        priorityOrder = priority
                                    )
                                )
                                }
                            }
                        }
                        result
                            .onSuccess { applySettingsSnapshotAndCacheEmergencyContacts(it) }
                            .onFailure {
                                sharedState.settingsLoading = false
                                sharedState.settingsError = it.message ?: "Could not save contact"
                            }
                        activeEditor = null
                    }
                }
            },
            onSaveInsurance = { existing, insuranceType, provider, policyNumber, policyHolderName, coverageType, expiryDate, coverageAmount, documentUri ->
                val repo = repository
                if (repo != null) {
                    scope.launch {
                        sharedState.settingsLoading = true
                        runCatching {
                            if (existing == null) {
                                retryOnce {
                                    repo.createInsuranceInfo(
                                        sharedState.accessToken,
                                    InsuranceInfoRequest(
                                        insuranceType = insuranceType,
                                        insuranceProvider = provider,
                                        insurancePolicyNumber = policyNumber,
                                        policyHolderName = policyHolderName.ifBlank { null },
                                        coverageType = coverageType.ifBlank { null },
                                        expiryDate = expiryDate.ifBlank { null },
                                        coverageAmount = coverageAmount.ifBlank { null },
                                        documentUri = documentUri.ifBlank { null }
                                    )
                                )
                                }
                            } else {
                                retryOnce {
                                    repo.updateInsuranceInfo(
                                        sharedState.accessToken,
                                    existing.insuranceId,
                                    InsuranceInfoUpdateRequest(
                                        insuranceType = insuranceType,
                                        insuranceProvider = provider,
                                        insurancePolicyNumber = policyNumber,
                                        policyHolderName = policyHolderName.ifBlank { null },
                                        coverageType = coverageType.ifBlank { null },
                                        expiryDate = expiryDate.ifBlank { null },
                                        coverageAmount = coverageAmount.ifBlank { null },
                                        documentUri = documentUri.ifBlank { null }
                                    )
                                )
                                }
                            }
                        }
                            .onSuccess { applySettingsSnapshotAndCacheEmergencyContacts(it) }
                            .onFailure {
                                sharedState.settingsLoading = false
                                sharedState.settingsError = it.message ?: "Could not save insurance"
                            }
                        activeEditor = null
                    }
                }
            },
            onDeleteEmergencyContact = { contact ->
                val repo = repository
                if (repo != null) {
                    scope.launch {
                        sharedState.settingsLoading = true
                        runCatching { retryOnce { repo.deleteEmergencyContact(sharedState.accessToken, contact.contactId) } }
                            .onSuccess { applySettingsSnapshotAndCacheEmergencyContacts(it) }
                            .onFailure {
                                sharedState.settingsLoading = false
                                sharedState.settingsError = it.message ?: "Could not delete contact"
                            }
                        activeEditor = null
                    }
                }
            },
            onDeleteInsurance = { insurance ->
                val userId = sharedState.userId
                val repo = repository
                if (repo != null) {
                    scope.launch {
                        sharedState.settingsLoading = true
                        runCatching { retryOnce { repo.deleteInsuranceInfo(sharedState.accessToken, insurance.insuranceId) } }
                            .onSuccess { applySettingsSnapshotAndCacheEmergencyContacts(it) }
                            .onFailure {
                                sharedState.settingsLoading = false
                                sharedState.settingsError = it.message ?: "Could not delete insurance"
                            }
                        activeEditor = null
                    }
                }
            },
            onMessage = onMessage
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            title = target.title,
            message = target.message,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                val userId = sharedState.userId
                val repo = repository
                if (repo != null) {
                    scope.launch {
                        sharedState.settingsLoading = true
                        when (target) {
                            is DeleteTarget.Emergency -> runCatching {
                                retryOnce { repo.deleteEmergencyContact(sharedState.accessToken, target.contact.contactId) }
                            }
                            is DeleteTarget.Insurance -> runCatching {
                                retryOnce { repo.deleteInsuranceInfo(sharedState.accessToken, target.insurance.insuranceId) }
                            }
                        }.onSuccess { applySettingsSnapshotAndCacheEmergencyContacts(it) }
                            .onFailure {
                                sharedState.settingsLoading = false
                                sharedState.settingsError = it.message ?: "Delete failed"
                            }
                        deleteTarget = null
                    }
                }
            }
        )
    }

    pendingPhoneUpdate?.let { phoneNumber ->
        PhoneOtpSheet(
            title = "Verify phone number",
            subtitle = "We need a code before saving this change.",
            phoneNumber = phoneNumber,
            debugCode = phoneOtpDebugCode,
            isSending = phoneOtpSending,
            isVerifying = phoneOtpVerifying,
            errorMessage = phoneOtpError,
            onRequestOtp = {
                scope.launch {
                    phoneOtpSending = true
                    phoneOtpError = null
                    try {
                        val response = sendPhoneOtp(phoneNumber)
                        if (response?.debugCode != null) {
                            phoneOtpDebugCode = response.debugCode
                        } else if (response == null) {
                            phoneOtpError = "Could not send verification code."
                        }
                    } catch (e: Exception) {
                        phoneOtpError = e.message ?: "Could not send verification code."
                    } finally {
                        phoneOtpSending = false
                    }
                }
            },
            onVerifyOtp = { otpCode ->
                scope.launch {
                    phoneOtpVerifying = true
                    phoneOtpError = null
                    try {
                        val verification = verifyPhoneOtp(phoneNumber, otpCode)
                        if (verification != null) {
                            sharedState.settingsLoading = true
                            runCatching {
                                retryOnce {
                                    repository?.saveUser(
                                        sharedState.accessToken,
                                        UserUpdateRequest(phoneNumber = phoneNumber)
                                    ) ?: throw IllegalStateException("Settings repository unavailable")
                                }
                            }.onSuccess {
                                applySettingsSnapshotAndCacheEmergencyContacts(it)
                                pendingPhoneUpdate = null
                                phoneOtpDebugCode = null
                            }.onFailure {
                                sharedState.settingsLoading = false
                                phoneOtpError = it.message ?: "Could not save phone number"
                            }
                        } else {
                            phoneOtpError = "Verification failed. Try again."
                        }
                    } catch (e: Exception) {
                        phoneOtpError = e.message ?: "Verification failed."
                    } finally {
                        phoneOtpVerifying = false
                    }
                }
            },
            onDismiss = {
                pendingPhoneUpdate = null
                phoneOtpDebugCode = null
                phoneOtpError = null
                phoneOtpSending = false
                phoneOtpVerifying = false
            }
        )
    }
}

private sealed class DeleteTarget(val title: String, val message: String) {
    class Emergency(val contact: EmergencyContactResponse) : DeleteTarget(
        title = "Delete emergency contact",
        message = "This will remove ${contact.contactNumber} from the saved emergency contacts."
    )

    class Insurance(val insurance: InsuranceInfoResponse) : DeleteTarget(
        title = "Delete insurance info",
        message = "This will remove ${insurance.insuranceProvider} from the saved insurance data."
    )
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Back",
                tint = if (isDark) Color.White else Color.Black
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "Settings",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black
            )
            Text(
                text = "Backend-loaded profile and safety data",
                fontSize = 13.sp,
                color = if (isDark) Color.White.copy(alpha = 0.68f) else SettingsMuted
            )
        }
    }
}

@Composable
private fun ProfileSummaryCard(
    name: String,
    phone: String,
    percent: Int,
    isDark: Boolean,
    onEditProfile: () -> Unit
) {
    val cardColor = if (isDark) SettingsCardDark else SettingsCardLight
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(SettingsBlueDark, SettingsBlue))),
                    contentAlignment = Alignment.Center
                ) {
    Text(
                        text = name.take(1).ifBlank { "U" }.uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )
                    Text(
                        text = phone.ifBlank { "No phone number saved" },
                        fontSize = 13.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else SettingsMuted
                    )
                }
                Surface(shape = RoundedCornerShape(999.dp), color = SettingsBlueDark.copy(alpha = 0.12f)) {
                    Text(
                        text = "$percent%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        color = SettingsBlueDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = percent / 100f,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = SettingsBlueDark,
                trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE6ECF5)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                Text("Complete User Profile", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, isDark: Boolean) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color.Black
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = if (isDark) Color.White.copy(alpha = 0.7f) else SettingsMuted
        )
    }
}

@Composable
private fun SettingsSectionCard(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) SettingsCardDark else SettingsCardLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun EditableFieldRow(
    label: String,
    value: String,
    isDark: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value.ifBlank { "Add $label" },
                color = if (value.isBlank()) SettingsDanger else if (isDark) Color.White.copy(alpha = 0.68f) else SettingsMuted,
                fontSize = 12.sp
            )
        }
        if (value.isBlank()) {
            IconButton(onClick = onEdit) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add $label", tint = SettingsDanger)
            }
        } else {
            IconButton(onClick = onEdit) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit $label", tint = SettingsBlueDark)
            }
        }
    }
}

@Composable
private fun MissingFieldCard(
    label: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF8FAFF))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = SettingsDanger)
        Text(
            text = label,
            modifier = Modifier.padding(start = 10.dp),
            color = if (isDark) Color.White else Color.Black,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmergencyContactCard(
    contact: EmergencyContactResponse,
    isDark: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = SettingsBlueDark)
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        text = contact.contactName?.takeIf { it.isNotBlank() }
                            ?: "Emergency contact",
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = contact.relationship,
                        color = if (isDark) Color.White.copy(alpha = 0.65f) else SettingsMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit contact", tint = SettingsBlueDark)
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete contact", tint = SettingsDanger)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Number: ${contact.contactNumber}",
                color = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black,
                fontSize = 12.sp
            )
            Text(
                text = "Priority: ${contact.priorityOrder}",
                color = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FieldRecordRow(
    label: String,
    value: String,
    isDark: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value.ifBlank { "Not set" },
                color = if (isDark) Color.White.copy(alpha = 0.68f) else SettingsMuted,
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit $label", tint = SettingsBlueDark)
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete $label", tint = SettingsDanger)
        }
    }
}

@Composable
private fun InsuranceRecordCard(
    insurance: InsuranceInfoResponse,
    isDark: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Policy, contentDescription = null, tint = SettingsBlueDark)
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        text = insurance.insuranceProvider.ifBlank { "Insurance policy" },
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = insurance.insuranceType,
                        color = if (isDark) Color.White.copy(alpha = 0.65f) else SettingsMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit insurance", tint = SettingsBlueDark)
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete insurance", tint = SettingsDanger)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Policy: ${insurance.insurancePolicyNumber}",
                color = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black,
                fontSize = 12.sp
            )
            insurance.policyHolderName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Holder: $it",
                    color = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black,
                    fontSize = 12.sp
                )
            }
            insurance.coverageType?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Coverage: $it",
                    color = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black,
                    fontSize = 12.sp
                )
            }
            insurance.expiryDate?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Expiry: $it",
                    color = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black,
                    fontSize = 12.sp
                )
            }
            insurance.coverageAmount?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Coverage amount: $it",
                    color = if (isDark) Color.White.copy(alpha = 0.78f) else Color.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SettingsBlueDark.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SettingsBlueDark)
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = title,
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = if (isDark) Color.White.copy(alpha = 0.65f) else SettingsMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DestructiveRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SettingsDanger.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SettingsDanger)
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = title,
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = if (isDark) Color.White.copy(alpha = 0.65f) else SettingsMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DividerRow(isDark: Boolean) {
    Divider(
        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE7ECF4)
    )
}

@Composable
private fun SettingsMessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    isDark: Boolean,
    accent: Color
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) SettingsCardDark else SettingsCardLight)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black)
                Text(message, color = if (isDark) Color.White.copy(alpha = 0.68f) else SettingsMuted, fontSize = 12.sp)
            }
        }
    }
}
