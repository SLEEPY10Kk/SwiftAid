package com.example.policeapp.ui.login

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.policeapp.AppMode
import com.example.policeapp.data.ResponderSession
import com.example.policeapp.data.model.ResponderProfile
import com.example.policeapp.firebase.FirebasePoliceRepository
import com.example.policeapp.theme.BackgroundBlack
import com.example.policeapp.theme.CardBackground
import com.example.policeapp.theme.GradientBlueEnd
import com.example.policeapp.theme.GradientBlueStart
import com.example.policeapp.theme.PrimaryBlue
import com.example.policeapp.theme.SurfaceBorder
import com.example.policeapp.theme.TextPrimary
import com.example.policeapp.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    mode: AppMode,
    onAuthenticated: (ResponderProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FirebasePoliceRepository() }

    var serviceCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        ResponderSession.load(context)?.takeIf { it.appMode == mode }?.let { saved ->
            serviceCode = saved.id
            serviceName = saved.name
            phoneNumber = saved.phoneNumber
            address = saved.address
            latitude = saved.latitude.toString()
            longitude = saved.longitude.toString()
        }
    }

    fun fillLocationFromDevice() {
        val location = context.bestLastLocation()
        if (location == null) {
            errorMessage = "Turn on location, then tap location again or enter coordinates manually."
        } else {
            latitude = location.latitude.toString()
            longitude = location.longitude.toString()
            errorMessage = null
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fillLocationFromDevice()
        } else {
            errorMessage = "Location permission is required for nearest-SOS routing."
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = PrimaryBlue,
        unfocusedBorderColor = SurfaceBorder,
        cursorColor = PrimaryBlue,
        focusedLabelColor = PrimaryBlue,
        unfocusedLabelColor = TextSecondary,
        focusedLeadingIconColor = PrimaryBlue,
        unfocusedLeadingIconColor = TextSecondary,
        focusedContainerColor = CardBackground.copy(alpha = 0.5f),
        unfocusedContainerColor = CardBackground.copy(alpha = 0.3f)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientBlueStart, GradientBlueEnd, BackgroundBlack)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(PrimaryBlue, RoundedCornerShape(16.dp))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = if (mode == AppMode.POLICE) "Police Service" else "Hospital Service",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Login or register this service to receive nearby SOS cases.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(28.dp))

            ServiceTextField(serviceCode, { serviceCode = it; errorMessage = null }, "Service Code", Icons.Default.Key, textFieldColors)
            Spacer(Modifier.height(12.dp))
            ServiceTextField(password, { password = it; errorMessage = null }, "Service Password", Icons.Default.Security, textFieldColors, isPassword = true)
            Spacer(Modifier.height(12.dp))
            ServiceTextField(serviceName, { serviceName = it; errorMessage = null }, "Service Name", Icons.Default.Business, textFieldColors)
            Spacer(Modifier.height(12.dp))
            ServiceTextField(
                phoneNumber,
                { phoneNumber = it; errorMessage = null },
                "Emergency Phone Number",
                Icons.Default.Phone,
                textFieldColors,
                keyboardType = KeyboardType.Phone
            )
            Spacer(Modifier.height(12.dp))
            ServiceTextField(address, { address = it; errorMessage = null }, "Service Address", Icons.Default.Title, textFieldColors)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ServiceTextField(
                    latitude,
                    { latitude = it; errorMessage = null },
                    "Latitude",
                    Icons.Default.LocationOn,
                    textFieldColors,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                ServiceTextField(
                    longitude,
                    { longitude = it; errorMessage = null },
                    "Longitude",
                    Icons.Default.LocationOn,
                    textFieldColors,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    if (context.hasLocationPermission()) {
                        fillLocationFromDevice()
                    } else {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, tint = PrimaryBlue)
                Text("Use current location", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
            }

            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    val parsedLatitude = latitude.trim().toDoubleOrNull()
                    val parsedLongitude = longitude.trim().toDoubleOrNull()
                    if (parsedLatitude == null || parsedLongitude == null) {
                        errorMessage = "Enter valid latitude and longitude."
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        repository.loginOrRegisterResponder(
                            mode = mode,
                            serviceCode = serviceCode,
                            password = password,
                            serviceName = serviceName,
                            phoneNumber = phoneNumber,
                            address = address,
                            latitude = parsedLatitude,
                            longitude = parsedLongitude
                        ).onSuccess { profile ->
                            ResponderSession.save(context, profile)
                            onAuthenticated(profile)
                        }.onFailure { error ->
                            errorMessage = error.message ?: "Could not save service."
                        }
                        isSaving = false
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "Login / Register Service",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ServiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    colors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        singleLine = true
    )
}

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun Context.bestLastLocation(): Location? {
    if (!hasLocationPermission()) return null
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return manager.getProviders(true)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}
