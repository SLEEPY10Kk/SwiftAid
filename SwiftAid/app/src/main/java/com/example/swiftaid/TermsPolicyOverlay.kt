package com.example.swiftaid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object TermsState {
    var isVisible by mutableStateOf(false)
}

@Composable
fun TermsPolicyOverlayHost(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        
        if (TermsState.isVisible) {
            TermsPolicyDialog(onDismiss = { TermsState.isVisible = false })
        }
    }
}

@Composable
fun TermsPolicyDialog(onDismiss: () -> Unit) {
    val isDark = LocalIsDark.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF1A1A1A) else Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            ) {
                Text(
                    "Terms & Privacy Policy",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    val textColor = if (isDark) Color.LightGray else Color.DarkGray
                    TermsTextSection("1. Service Overview", "SwiftAid provides emergency assistance coordination by sharing your predefined medical and insurance data with responders.", textColor)
                    TermsTextSection("2. Data Privacy", "Your personal data is encrypted and stored locally. Medical reports are only accessible by authorized emergency services when an SOS is triggered.", textColor)
                    TermsTextSection("3. Location Tracking", "The app requires background location access to provide accurate coordinates to responders during emergencies.", textColor)
                    TermsTextSection("4. Liability", "SwiftAid is a supplementary tool and should not be your only method of contacting emergency services. We are not liable for delayed responses from 3rd party services.", textColor)
                    TermsTextSection("5. User Conduct", "Misuse of the SOS feature for non-emergencies may lead to account suspension.", textColor)
                }
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("I Understand", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TermsTextSection(title: String, body: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 14.sp, color = color.copy(alpha = 0.8f), lineHeight = 20.sp)
    }
}
