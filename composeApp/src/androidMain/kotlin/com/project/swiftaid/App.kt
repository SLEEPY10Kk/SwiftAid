package com.project.swiftaid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.compose_multiplatform

class AppSharedState {
    var username by mutableStateOf("")
    var fullName by mutableStateOf("")
    var phone by mutableStateOf("")
    var password by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var country by mutableStateOf("")
    var exactArea by mutableStateOf("")

    var bloodGroup by mutableStateOf("")
    var allergies by mutableStateOf("")
    var chronicConditions by mutableStateOf("")
    var isReportAdded by mutableStateOf(false)
    var reportUri: android.net.Uri? by mutableStateOf(null)
}

val LocalSharedState = staticCompositionLocalOf<AppSharedState> { error("No SharedState provided") }

@Composable
@Preview
fun App() {
    var currentScreen by remember { mutableStateOf("SignIn") }
    var currentLanguageCode by remember { mutableStateOf("en") }
    val sharedState = remember { AppSharedState() }

    CompositionLocalProvider(
        LocalLanguage provides currentLanguageCode,
        LocalLanguageChange provides { code -> currentLanguageCode = code },
        LocalSharedState provides sharedState
    ) {
        MaterialTheme {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(500)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(500)
                )
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                "SignIn" -> SignInScreen(
                    onSignInClick = { },
                    onSignUpClick = { currentScreen = "UserInfo" }
                )
                "UserInfo" -> CreateAccountScreen(
                    onCreateAccount = { _, _, _, _, _, _, _, _ ->
                        currentScreen = "MedicalInfo"
                    },
                    onBack = { currentScreen = "SignIn" }
                )
                "MedicalInfo" -> MedicalInfoScreen(
                    onSaveAndContinue = { _, _, _, _ ->
                        currentScreen = "SignIn"
                    },
                    onBack = { currentScreen = "UserInfo" }
                )
            }
        }
    }
}
}
}