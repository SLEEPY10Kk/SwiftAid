package com.example.swiftaid

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

data class Claim(
    val id: String,
    val title: String,
    val date: String,
    val status: String,
    val amount: String,
    val statusColor: Color,
    val description: String = ""
)

data class Insurance(
    val id: String = java.util.UUID.randomUUID().toString(),
    var provider: String = "",
    var policyNumber: String = "",
    var policyHolderName: String = "",
    var coverageType: String = "",
    var expiryDate: String = "",
    var coverageAmount: String = "",
    var documentUri: Uri? = null
)

data class MedicalDocument(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "",
    var uri: Uri? = null
)

data class SignupProfileDraft(
    val username: String,
    val fullName: String,
    val age: Int,
    val gender: String,
    val phoneParts: PhoneParts,
    val password: String,
    val city: String,
    val state: String,
    val country: String,
    val exactArea: String
) {
    fun toRequest() = CompleteProfileRequest(
        username = username,
        fullName = fullName,
        age = age,
        gender = gender,
        dialCode = phoneParts.dialCode,
        phone = phoneParts.localNumber,
        country = country,
        state = state,
        city = city,
        area = exactArea,
        password = password
    )
}

class AppSharedState {
    var username by mutableStateOf("")
    var fullName by mutableStateOf("")
    var phone by mutableStateOf("")
    var password by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var country by mutableStateOf("")
    var exactArea by mutableStateOf("")

    var age by mutableStateOf("")
    var gender by mutableStateOf("")
    var dob by mutableStateOf("")
    var residentialAddress by mutableStateOf("")
    var emergencyContactName by mutableStateOf("")
    var emergencyContactPhone by mutableStateOf("")

    var bloodGroup by mutableStateOf("")
    var allergies by mutableStateOf("")
    var chronicConditions by mutableStateOf("")
    var medicalHistory by mutableStateOf("")
    var isReportAdded by mutableStateOf(false)
    var reportUri: android.net.Uri? by mutableStateOf(null)

    var medicalHistoryDocuments = mutableStateListOf<MedicalDocument>()
    var insurances = mutableStateListOf<Insurance>()
    var claims = mutableStateListOf<Claim>()
    var isSafetyProfileComplete by mutableStateOf(false)
    var safetyProfilePercent by mutableStateOf(0)
    var accessToken by mutableStateOf("")
    var refreshToken by mutableStateOf("")
    var userId by mutableStateOf<String?>(null)
    var settingsSnapshot by mutableStateOf<SettingsSnapshot?>(null)
    var settingsLoading by mutableStateOf(false)
    var settingsError by mutableStateOf("")
}

val LocalSharedState = staticCompositionLocalOf<AppSharedState> { error("No SharedState provided") }

@Composable
@Preview
fun App(
    onGoogleSignInClick: ((AuthResult) -> Unit) -> Unit = {},
    onGoogleSignUpClick: ((AuthResult) -> Unit) -> Unit = {},
    tokenStorage: TokenStorage? = null,
    api: AuthApi? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Loading") }
    var currentLanguageCode by remember { mutableStateOf("en") }
    var selectedExploreCategory by remember { mutableStateOf<String?>(null) }
    var pendingSignupProfile by remember { mutableStateOf<SignupProfileDraft?>(null) }
    var signupOtpDebugCode by remember { mutableStateOf<String?>(null) }
    var signupOtpError by remember { mutableStateOf<String?>(null) }
    var signupOtpSending by remember { mutableStateOf(false) }
    var signupOtpVerifying by remember { mutableStateOf(false) }
    val sharedState = remember { 
        AppSharedState().apply {
            claims.addAll(listOf(
                Claim("CLM-9821", "Accident Recovery", "Oct 12, 2023", "In Review", "$1,200", Color(0xFFE07000), "Emergency accident recovery and towing services."),
                Claim("CLM-8742", "Emergency Room", "Sep 05, 2023", "Approved", "$450", Color(0xFF18A558), "Hospitalization costs for acute appendicitis."),
                Claim("CLM-7612", "Vehicle Towing", "Aug 20, 2023", "Completed", "$150", Color(0xFF18A558), "Standard vehicle towing after engine failure.")
            ))
        }
    }
    
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    var themeMode by remember { mutableStateOf("system") }
    
    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    suspend fun refreshAndRetry(): Boolean {
        val refreshToken = sharedState.refreshToken.ifBlank { tokenStorage?.getRefreshToken().orEmpty() }
        if (refreshToken.isBlank() || api == null) return false
        val response = api.refreshTokens(refreshToken)
        if (response.status != HttpStatusCode.OK) return false
        val body = response.body<AuthResponse>()
        sharedState.accessToken = body.accessToken
        sharedState.refreshToken = body.refreshToken
        sharedState.userId = extractUserIdFromJwt(body.accessToken)
        tokenStorage?.saveTokens(body.accessToken, body.refreshToken)
        return true
    }

    fun loadCurrentSettingsSnapshot() {
        val currentApi = api ?: return
        val accessToken = sharedState.accessToken
        if (accessToken.isBlank()) return
        scope.launch {
            runCatching { currentApi.loadUserSettings(accessToken) }
                .onSuccess { snapshot ->
                    sharedState.applySettingsSnapshot(snapshot.toSnapshot())
                    sharedState.userId = snapshot.user.id
                }
        }
    }

    fun goToSignedOut() {
        tokenStorage?.clearTokens()
        sharedState.clearSessionData()
        pendingSignupProfile = null
        signupOtpDebugCode = null
        signupOtpError = null
        signupOtpSending = false
        signupOtpVerifying = false
        currentScreen = "SignIn"
    }

    fun loadSafetyCompletion() {
        val currentApi = api ?: return
        val accessToken = sharedState.accessToken
        if (accessToken.isBlank()) return
        scope.launch {
            runCatching { currentApi.loadUserSettings(accessToken) }
                .recoverCatching {
                    if (refreshAndRetry()) currentApi.loadUserSettings(sharedState.accessToken) else throw it
                }
                .onSuccess { snapshot ->
                    sharedState.applySettingsSnapshot(snapshot.toSnapshot())
                    sharedState.userId = snapshot.user.id
                }
        }
    }

    suspend fun sendSignupPhoneOtp(phoneNumber: String): PhoneOtpResponse? {
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
            if (refreshAndRetry()) {
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

    suspend fun verifySignupPhoneOtp(phoneNumber: String, otpCode: String): PhoneOtpResponse? {
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
            if (refreshAndRetry()) {
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

    LaunchedEffect(Unit) {
        val savedRefreshToken = tokenStorage?.getRefreshToken()
        if (savedRefreshToken.isNullOrBlank() || api == null) {
            currentScreen = "SignIn"
            return@LaunchedEffect
        }
        try {
            val response = api.validateSession(savedRefreshToken)
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<AuthResponse>()
                tokenStorage.saveTokens(body.accessToken, body.refreshToken)
                sharedState.accessToken = body.accessToken
                sharedState.refreshToken = body.refreshToken
                sharedState.userId = extractUserIdFromJwt(body.accessToken)
                currentScreen = if (body.isComplete == true) "MapUI" else "UserInfo"
                if (body.isComplete == true) loadSafetyCompletion()
            } else {
                goToSignedOut()
            }
        } catch (_: Exception) {
            goToSignedOut()
        }
    }

    CompositionLocalProvider(
        LocalLanguage provides currentLanguageCode,
        LocalLanguageChange provides { code -> currentLanguageCode = code },
        LocalSharedState provides sharedState,
        LocalThemeMode provides themeMode,
        LocalThemeChange provides { mode -> themeMode = mode },
        LocalIsDark provides isDark
    ) {
        TermsPolicyOverlayHost {
            MaterialTheme {
                val screenOrder = listOf("Loading", "SignIn", "UserInfo", "EmergencyInfo", "MedicalInfo", "InsuranceInfo", "MapUI", "Claims", "Explore", "Settings", "UserProfile")
                fun getScreenIndex(screen: String) = screenOrder.indexOf(screen)

                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { _ ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val initialIndex = getScreenIndex(initialState)
                            val targetIndex = getScreenIndex(targetState)

                            if (targetIndex > initialIndex) {
                                slideInHorizontally(animationSpec = tween(400)) { it } togetherWith
                                        slideOutHorizontally(animationSpec = tween(400)) { -it }
                            } else {
                                slideInHorizontally(animationSpec = tween(400)) { -it } togetherWith
                                        slideOutHorizontally(animationSpec = tween(400)) { it }
                            }
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            "Loading" -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF336CFC))
                            }

                            "SignIn" -> SignInScreen(
                                onSignInClick = { email, password ->
                                    scope.launch {
                                        try {
                                            val response = api?.emailLogin(email, password)
                                            if (response?.status == HttpStatusCode.OK) {
                                                val body = response.body<AuthResponse>()
                                                tokenStorage?.saveTokens(body.accessToken, body.refreshToken)
                                                sharedState.accessToken = body.accessToken
                                                sharedState.refreshToken = body.refreshToken
                                                sharedState.userId = extractUserIdFromJwt(body.accessToken)
                                                loadSafetyCompletion()
                                                currentScreen = "MapUI"
                                            } else {
                                                showMessage("Login failed. Check your credentials.")
                                            }
                                        } catch (_: Exception) {
                                            showMessage("Network error. Please try again.")
                                        }
                                    }
                                },
                                onSignUpClick = {
                                    onGoogleSignUpClick { result ->
                                        when (result) {
                                            is AuthResult.Success -> {
                                                sharedState.accessToken = result.accessToken
                                                sharedState.refreshToken = result.refreshToken
                                                sharedState.userId = extractUserIdFromJwt(result.accessToken)
                                                currentScreen = "UserInfo"
                                            }
                                            AuthResult.UserAlreadyExists -> showMessage("Already registered. Please sign in.")
                                            is AuthResult.Failure -> showMessage(result.message)
                                            AuthResult.Error -> showMessage("Google registration failed.")
                                        }
                                    }
                                },
                                onGoogleSignInClick = {
                                    onGoogleSignInClick { result ->
                                        when (result) {
                                            is AuthResult.Success -> {
                                                sharedState.accessToken = result.accessToken
                                                sharedState.refreshToken = result.refreshToken
                                                sharedState.userId = extractUserIdFromJwt(result.accessToken)
                                                if (result.isComplete == true) {
                                                    loadSafetyCompletion()
                                                    currentScreen = "MapUI"
                                                } else {
                                                    currentScreen = "UserInfo"
                                                }
                                            }
                                            is AuthResult.Failure -> showMessage(result.message)
                                            else -> showMessage("Google sign in failed.")
                                        }
                                    }
                                }
                            )

                            "UserInfo" -> CreateAccountScreen(
                                onCreateAccount = { username, fullName, age, gender, phone, password, city, state, country, exactArea ->
                                    sharedState.username = username
                                    sharedState.fullName = fullName
                                    sharedState.phone = phone
                                    sharedState.password = password
                                    sharedState.city = city
                                    sharedState.state = state
                                    sharedState.country = country
                                    sharedState.exactArea = exactArea
                                    sharedState.age = age
                                    sharedState.gender = gender
                                    scope.launch {
                                        try {
                                            val phoneParts = parsePhoneParts(phone)
                                            signupOtpError = null
                                            signupOtpDebugCode = null
                                            signupOtpSending = true
                                            val otpResponse = sendSignupPhoneOtp(phone)
                                            signupOtpSending = false
                                            if (otpResponse != null) {
                                                pendingSignupProfile = SignupProfileDraft(
                                                    username = username,
                                                    fullName = fullName,
                                                    age = age.toIntOrNull() ?: 0,
                                                    gender = gender,
                                                    phoneParts = phoneParts,
                                                    password = password,
                                                    city = city,
                                                    state = state,
                                                    country = country,
                                                    exactArea = exactArea
                                                )
                                                signupOtpDebugCode = otpResponse.debugCode
                                            } else {
                                                showMessage("Could not send verification code.")
                                            }
                                        } catch (e: Exception) {
                                            signupOtpSending = false
                                            if (e.message == "PHONE_TAKEN") {
                                                showMessage("NUMBER ALREADY TAKEN, TRY A DIFFERENT ONE")
                                            } else {
                                                showMessage(e.message ?: "Could not send verification code.")
                                            }
                                        }
                                    }
                                },
                                onBack = { goToSignedOut() }
                            )

                            "EmergencyInfo" -> EmergencyContactOnboardingScreen(
                                onSave = { contactName, phone, relationship, priority ->
                                    scope.launch {
                                        if (api != null) {
                                            runCatching {
                                                api.createEmergencyContact(
                                                    sharedState.accessToken,
                                                    EmergencyContactRequest(
                                                        contactName = contactName,
                                                        contactNumber = phone,
                                                        relationship = relationship,
                                                        priorityOrder = priority
                                                    )
                                                )
                                            }
                                            loadSafetyCompletion()
                                        }
                                        currentScreen = "MapUI"
                                    }
                                },
                                onSkip = { currentScreen = "MapUI" },
                                onMessage = { message -> showMessage(message) }
                            )

                            "MapUI" -> MapWithPermission(
                                onSettings = { currentScreen = "Settings" },
                                onClaims = { currentScreen = "Claims" },
                                onExploreClick = { category ->
                                    selectedExploreCategory = category
                                    currentScreen = "Explore"
                                }
                            )

                            "Explore" -> ExploreScreen(
                                initialCategory = selectedExploreCategory,
                                onBack = { currentScreen = "MapUI" }
                            )
                            "Claims" -> ClaimsScreen(
                                onBack = { currentScreen = "MapUI" }
                            )
                            "Settings" -> SettingsScreenV2(
                                api = api,
                                tokenStorage = tokenStorage,
                                onBack = { currentScreen = "MapUI" },
                                onSignOut = { goToSignedOut() },
                                onProfileClick = { currentScreen = "UserProfile" },
                                onMessage = { message -> showMessage(message) }
                            )
                            "UserProfile" -> UserProfileScreen(
                                onBack = { currentScreen = "Settings" }
                            )
                        }
                    }
                }

                pendingSignupProfile?.let { draft ->
                    val phoneNumber = formatPhoneNumber(draft.phoneParts.dialCode, draft.phoneParts.localNumber)
                    PhoneOtpSheet(
                        title = "Verify phone number",
                        subtitle = "Enter the code sent before we complete your profile.",
                        phoneNumber = phoneNumber,
                        debugCode = signupOtpDebugCode,
                        isSending = signupOtpSending,
                        isVerifying = signupOtpVerifying,
                        errorMessage = signupOtpError,
                        onRequestOtp = {
                            scope.launch {
                                signupOtpSending = true
                                signupOtpError = null
                                try {
                                    val otpResponse = sendSignupPhoneOtp(phoneNumber)
                                    if (otpResponse != null) {
                                        signupOtpDebugCode = otpResponse.debugCode
                                    } else {
                                        signupOtpError = "Could not send verification code."
                                    }
                                } catch (e: Exception) {
                                    signupOtpError = if (e.message == "PHONE_TAKEN") {
                                        "NUMBER ALREADY TAKEN, TRY A DIFFERENT ONE"
                                    } else {
                                        e.message ?: "Could not send verification code."
                                    }
                                } finally {
                                    signupOtpSending = false
                                }
                            }
                        },
                        onVerifyOtp = { otpCode ->
                            scope.launch {
                                signupOtpVerifying = true
                                signupOtpError = null
                                try {
                                    val verification = verifySignupPhoneOtp(phoneNumber, otpCode)
                                    if (verification != null) {
                                        var response = api?.completeProfile(sharedState.accessToken, draft.toRequest())
                                        if (response?.status == HttpStatusCode.Unauthorized && refreshAndRetry()) {
                                            response = api?.completeProfile(sharedState.accessToken, draft.toRequest())
                                        }
                                        if (response?.status == HttpStatusCode.OK) {
                                            sharedState.safetyProfilePercent = 40
                                            pendingSignupProfile = null
                                            signupOtpDebugCode = null
                                            signupOtpError = null
                                            signupOtpSending = false
                                            signupOtpVerifying = false
                                            currentScreen = "EmergencyInfo"
                                        } else {
                                            signupOtpVerifying = false
                                            signupOtpError = "Could not complete profile."
                                        }
                                    } else {
                                        signupOtpVerifying = false
                                        signupOtpError = "Verification failed. Try again."
                                    }
                                } catch (e: Exception) {
                                    signupOtpVerifying = false
                                    signupOtpError = e.message ?: "Verification failed."
                                }
                            }
                        },
                        onDismiss = {
                            pendingSignupProfile = null
                            signupOtpDebugCode = null
                            signupOtpError = null
                            signupOtpSending = false
                            signupOtpVerifying = false
                        }
                    )
                }
            }
        }
    }
}
