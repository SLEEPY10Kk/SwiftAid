package com.example.swiftaid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneOtpSheet(
    title: String,
    subtitle: String,
    phoneNumber: String,
    debugCode: String? = null,
    isSending: Boolean,
    isVerifying: Boolean,
    errorMessage: String? = null,
    onRequestOtp: () -> Unit,
    onVerifyOtp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var otpCode by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Verification will be sent to $phoneNumber")

            if (!debugCode.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Test OTP: $debugCode", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = otpCode,
                onValueChange = { otpCode = it.filter(Char::isDigit).take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("6-digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            errorMessage?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isSending && !isVerifying
                ) {
                    Text("Cancel")
                }
                OutlinedButton(
                    onClick = onRequestOtp,
                    modifier = Modifier.weight(1f),
                    enabled = !isSending && !isVerifying
                ) {
                    Text(if (isSending) "Sending..." else "Resend")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onVerifyOtp(otpCode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = otpCode.length == 6 && !isSending && !isVerifying
            ) {
                if (isVerifying) {
                    CircularProgressIndicator()
                } else {
                    Text("Verify and continue")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
