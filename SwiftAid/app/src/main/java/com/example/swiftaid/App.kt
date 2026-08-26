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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
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
    fun toRequest(): CompleteProfileRequest? {
        val parsedGender = gender.trim().ifBlank { return null }
        return CompleteProfileRequest(
            username = username,
            fullName = fullName,
            age = age,
            gender = parsedGender,
            dialCode = phoneParts.dialCode,
            phone = phoneParts.localNumber,
            country = country,
            state = state,
            city = city,
            area = exactArea,
            password = password
        )
    }
}

class AppSharedState {
    var signupEmail by mutableStateOf("")
    var signupPassword by mutableStateOf("")
    var signupConfirmPassword by mutableStateOf("")
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
    var currentScreen by remember { mutableStateOf(if (tokenStorage != null && api != null) "Loading" else "SignIn") }
    var currentLanguageCode by remember { mutableStateOf("en") }
    var selectedExploreCategory by remember { mutableStateOf<String?>(null) }
    var pendingSignupProfile by remember { mutableStateOf<SignupProfileDraft?>(null) }
    var signupOtpDebugCode by remember { mutableStateOf<String?>(null) }
    var signupOtpError by remember { mutableStateOf<String?>(null) }
    var signupOtpSending by remember { mutableStateOf(false) }
    var signupOtpVerifying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sharedState = remember { AppSharedState() }
    
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
        val currentApi = api ?: return false
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
                    UserEmergencyProfile.save(context, sharedState.fullName, sharedState.phone)
                    EmergencySmsDispatcher.saveEmergencyContacts(
                        context,
                        snapshot.emergencyContacts
                            .map { it.contactNumber }
                            .filter { it.isNotBlank() }
                            .joinToString(separator = "\n")
                    )
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

    suspend fun sendSignupPhoneOtp(phoneNumber: String): PhoneOtpResponse? {
        val currentApi = api ?: return null
        if (sharedState.accessToken.isBlank()) return null
        val response = currentApi.sendPhoneOtp(sharedState.accessToken, phoneNumber)
        if (response.status == HttpStatusCode.OK) return response.body()
        if (response.status == HttpStatusCode.Unauthorized && refreshAndRetry()) {
            val retry = currentApi.sendPhoneOtp(sharedState.accessToken, phoneNumber)
            if (retry.status == HttpStatusCode.OK) return retry.body()
        }
        if (response.status == HttpStatusCode.Conflict) throw IllegalStateException("PHONE_TAKEN")
        throw IllegalStateException(runCatching { response.body<ErrorResponse>().detail }.getOrDefault("Could not send verification code."))
    }

    suspend fun verifySignupPhoneOtp(phoneNumber: String, otpCode: String): PhoneOtpResponse? {
        val currentApi = api ?: return null
        if (sharedState.accessToken.isBlank()) return null
        val response = currentApi.verifyPhoneOtp(sharedState.accessToken, phoneNumber, otpCode)
        if (response.status == HttpStatusCode.OK) return response.body()
        if (response.status == HttpStatusCode.Unauthorized && refreshAndRetry()) {
            val retry = currentApi.verifyPhoneOtp(sharedState.accessToken, phoneNumber, otpCode)
            if (retry.status == HttpStatusCode.OK) return retry.body()
        }
        throw IllegalStateException(runCatching { response.body<ErrorResponse>().detail }.getOrDefault("Verification failed."))
    }

    LaunchedEffect(Unit) {
        val savedRefreshToken = tokenStorage?.getRefreshToken()
        val savedAccessToken = tokenStorage?.getAccessToken()
        if (api == null || savedRefreshToken.isNullOrBlank()) {
            currentScreen = "SignIn"
            return@LaunchedEffect
        }
        fun continueWithCachedSession() {
            sharedState.accessToken = savedAccessToken.orEmpty()
            sharedState.refreshToken = savedRefreshToken
            sharedState.userId = savedAccessToken?.let { extractUserIdFromJwt(it) }
            currentScreen = "MapUI"
        }
        runCatching {
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
                continueWithCachedSession()
            }
        }.onFailure {
            continueWithCachedSession()
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
                val screenOrder = listOf("Loading", "SignIn", "UserInfo", "EmergencyInfo", "MapUI", "Claims", "Explore", "Settings", "UserProfile")
                fun getScreenIndex(screen: String) = screenOrder.indexOf(screen)

                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { _ ->
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val initialIndex = getScreenIndex(initialState)
                        val targetIndex = getScreenIndex(targetState)
                        
                        if (targetIndex > initialIndex) {
                            // Moving forward -> slide in from right, slide out to left
                            slideInHorizontally(animationSpec = tween(400)) { it } togetherWith
                                    slideOutHorizontally(animationSpec = tween(400)) { -it }
                        } else {
                            // Moving backward -> slide in from left, slide out to right
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
                                val loginEmail = email.trim()
                                scope.launch {
                                    if (loginEmail.isBlank()) {
                                        showMessage("Enter your email address.")
                                        return@launch
                                    }
                                    if (password.isBlank()) {
                                        showMessage("Enter your password.")
                                        return@launch
                                    }
                                    if (api == null || tokenStorage == null) {
                                        currentScreen = "MapUI"
                                        return@launch
                                    }
                                    runCatching {
                                        val response = api.emailLogin(loginEmail, password)
                                        if (response.status == HttpStatusCode.OK) {
                                            val body = response.body<AuthResponse>()
                                            tokenStorage.saveTokens(body.accessToken, body.refreshToken)
                                            sharedState.accessToken = body.accessToken
                                            sharedState.refreshToken = body.refreshToken
                                            sharedState.userId = extractUserIdFromJwt(body.accessToken)
                                            loadSafetyCompletion()
                                            currentScreen = "MapUI"
                                        } else {
                                            showMessage(response.loginFailureMessage())
                                        }
                                    }.onFailure {
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
                                            if (result.isComplete == true) {
                                                loadSafetyCompletion()
                                                currentScreen = "MapUI"
                                            } else {
                                                currentScreen = "UserInfo"
                                            }
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
                                UserEmergencyProfile.save(context, fullName, phone)
                                if (api != null && sharedState.accessToken.isNotBlank()) {
                                    scope.launch {
                                        runCatching {
                                            val phoneParts = parsePhoneParts(phone)
                                            signupOtpError = null
                                            signupOtpDebugCode = null
                                            signupOtpSending = true
                                            val otpResponse = sendSignupPhoneOtp(formatPhoneNumber(phoneParts.dialCode, phoneParts.localNumber))
                                            signupOtpSending = false
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
                                            signupOtpDebugCode = otpResponse?.debugCode
                                        }.onFailure { error ->
                                            signupOtpSending = false
                                            showMessage(
                                                if (error.message == "PHONE_TAKEN") {
                                                    "NUMBER ALREADY TAKEN, TRY A DIFFERENT ONE"
                                                } else {
                                                    error.message ?: "Could not send verification code."
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    currentScreen = "EmergencyInfo"
                                }
                            },
                            onBack = { currentScreen = "SignIn" }
                        )
                        "EmergencyInfo" -> EmergencyContactOnboardingScreen(
                            onSave = { contactName, contactPhone, relationship, priority ->
                                scope.launch {
                                    if (api != null) {
                                        runCatching {
                                            api.createEmergencyContact(
                                                sharedState.accessToken,
                                                EmergencyContactRequest(
                                                    contactName = contactName,
                                                    contactNumber = contactPhone,
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
                            onBack = { currentScreen = "MapUI" },
                            api = api,
                            tokenStorage = tokenStorage,
                            onMessage = { message -> showMessage(message) }
                        )
                        "Settings" -> {
                            if (api != null) {
                                SettingsScreenV2(
                                    api = api,
                                    tokenStorage = tokenStorage,
                                    onBack = { currentScreen = "MapUI" },
                                    onSignOut = { goToSignedOut() },
                                    onProfileClick = { currentScreen = "UserProfile" },
                                    onMessage = { message -> showMessage(message) }
                                )
                            } else {
                                SettingsScreen(
                                    onBack = { currentScreen = "MapUI" },
                                    onSignOut = { goToSignedOut() },
                                    onProfileClick = { currentScreen = "UserProfile" }
                                )
                            }
                        }
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
                                runCatching {
                                    signupOtpDebugCode = sendSignupPhoneOtp(phoneNumber)?.debugCode
                                }.onFailure { error ->
                                    signupOtpError = error.message ?: "Could not send verification code."
                                }
                                signupOtpSending = false
                            }
                        },
                        onVerifyOtp = { otpCode ->
                            scope.launch {
                                signupOtpVerifying = true
                                signupOtpError = null
                                runCatching {
                                    verifySignupPhoneOtp(phoneNumber, otpCode)
                                    val profileRequest = draft.toRequest()
                                    if (profileRequest == null) {
                                        signupOtpError = "Enter your age (1–120) and select a gender before continuing."
                                        signupOtpVerifying = false
                                        return@launch
                                    }
                                    var response = api?.completeProfile(
                                        sharedState.accessToken,
                                        profileRequest
                                    )
                                    if (response?.status == HttpStatusCode.Unauthorized && refreshAndRetry()) {
                                        response = api?.completeProfile(
                                            sharedState.accessToken,
                                            profileRequest
                                        )
                                    }
                                    if (response?.status == HttpStatusCode.OK) {
                                        sharedState.safetyProfilePercent = 40
                                        pendingSignupProfile = null
                                        signupOtpDebugCode = null
                                        signupOtpError = null
                                        signupOtpSending = false
                                        signupOtpVerifying = false
                                        currentScreen = "EmergencyInfo"
                                    } else if (response != null) {
                                        signupOtpError = response.profileCompletionFailureMessage()
                                    } else {
                                        signupOtpError = "Could not complete profile."
                                    }
                                }.onFailure { error ->
                                    signupOtpError = error.message ?: "Verification failed."
                                }
                                signupOtpVerifying = false
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

private suspend fun HttpResponse.profileCompletionFailureMessage(): String {
    val rawBody = runCatching { bodyAsText() }.getOrDefault("")
    return when {
        rawBody.contains("PASSWORD_TOO_LONG") ->
            "Password is too long for secure hashing. Use 72 bytes or less."
        rawBody.contains("PASSWORD_TOO_SHORT") ->
            "Password must be at least 8 characters."
        rawBody.contains("PHONE_NOT_VERIFIED") ->
            "Verify your phone number before completing the profile."
        rawBody.contains("FIELD_REQUIRED") ->
            "Complete all required profile fields."
        status == HttpStatusCode.UnprocessableEntity ->
            "Profile invalid: enter age between 1 and 120 and select gender."
        else -> {
            val statusDetail = if (rawBody.isNotBlank()) ": $rawBody" else " (Status: ${status.value})"
            "Could not complete profile$statusDetail"
        }
    }
}

private suspend fun HttpResponse.loginFailureMessage(): String {
    val detail = runCatching { body<ErrorResponse>().detail }.getOrNull()
    return when (detail) {
        "USER_NOT_FOUND" -> "No account found for this email. Sign up with Google first."
        "USE_GOOGLE_LOGIN" -> "This account was created with Google. Sign in with Google to finish setup and set a password."
        "PROFILE_INCOMPLETE" -> "Finish your profile with Google sign-in before using password login."
        "INVALID_PASSWORD" -> "Incorrect password. Please try again."
        "PASSWORD_REQUIRED" -> "Enter your password."
        else -> when (status) {
            HttpStatusCode.UnprocessableEntity -> "Enter a valid email and password."
            HttpStatusCode.Unauthorized -> "Incorrect email or password."
            else -> "Login failed. Please try again."
        }
    }
}
