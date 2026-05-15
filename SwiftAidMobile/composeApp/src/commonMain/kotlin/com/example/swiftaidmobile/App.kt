package com.example.swiftaidmobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

import swiftaid.swiftaidmobile.composeapp.generated.resources.Res
import swiftaid.swiftaidmobile.composeapp.generated.resources.compose_multiplatform

@Composable
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        val locationManager = rememberLocationManager()
        var currentLocation by remember { mutableStateOf<Location?>(null) }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (currentLocation != null) {
                Text(
                    text = "Your Location: ${currentLocation?.latitude}, ${currentLocation?.longitude}",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = "Getting location...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                if (locationManager.hasPermission()) {
                    locationManager.startLocationUpdates { location ->
                        currentLocation = location
                    }
                } else {
                    locationManager.requestPermission()
                }
            }) {
                Text("Update Location")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }

        // Start updates automatically if permission is already granted
        LaunchedEffect(Unit) {
            if (locationManager.hasPermission()) {
                locationManager.startLocationUpdates { location ->
                    currentLocation = location
                }
            }
        }
    }
}
