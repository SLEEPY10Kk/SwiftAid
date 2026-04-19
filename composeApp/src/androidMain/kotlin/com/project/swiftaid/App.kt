package com.project.swiftaid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource

import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    var currentScreen by remember { mutableStateOf("SignIn") }

    MaterialTheme {
        when (currentScreen) {
            "SignIn" -> SignInScreen(
                onSignInClick = { currentScreen = "MedicalInfo" },
                onSignUpClick = { currentScreen = "MedicalInfo" }
            )
            "MedicalInfo" -> MedicalInfoScreen(
                onSaveAndContinue = { _, _, _, _ ->
                    currentScreen = "SignIn"
                }
            )
        }
    }
}