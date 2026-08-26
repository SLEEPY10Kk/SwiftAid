package com.example.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OnboardingTop = Color(0xFF283B5A)
private val OnboardingBottom = Color(0xFF1E212B)
private val OnboardingBlue = Color(0xFF336CFC)

@Composable
fun EmergencyContactOnboardingScreen(
    onSave: (String?, String, String, Int) -> Unit,
    onSkip: () -> Unit,
    onMessage: (String) -> Unit = {}
) {
    var contactName by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var priorityOrder by remember { mutableStateOf("1") }
    var priorityError by remember { mutableStateOf<String?>(null) }

    OnboardingShell(
        title = "Emergency Contact",
        subtitle = "Add a trusted contact for faster emergency assistance.",
        onSkip = onSkip,
        saveEnabled = contactNumber.isNotBlank() &&
            relationship.isNotBlank() &&
            validatePriorityOrder(priorityOrder) == null,
        onSave = {
            priorityError = validatePriorityOrder(priorityOrder)
            if (contactNumber.isNotBlank() && priorityError == null) {
                onSave(
                    contactName.ifBlank { null },
                    contactNumber,
                    relationship,
                    priorityOrder.toIntOrNull() ?: 1
                )
            }
        }
    ) {
        EmergencyContactPickerButton(
            buttonText = "Add a contact from your contacts",
            isDark = true,
            onContactPicked = { pickedName, pickedNumber ->
                contactName = pickedName
                contactNumber = pickedNumber
                onMessage("Contact loaded from address book.")
            },
            onMessage = onMessage
        )
        Spacer(Modifier.height(12.dp))
        CustomTextField(
            value = contactName,
            onValueChange = { contactName = it },
            placeholder = "Contact name",
            leadingIcon = Icons.Default.ContactPhone,
            isDark = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = contactNumber,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contact number") },
            singleLine = true,
            readOnly = true
        )
        Spacer(Modifier.height(12.dp))
        CustomTextField(
            value = relationship,
            onValueChange = { relationship = it },
            placeholder = "Relationship",
            leadingIcon = Icons.Default.ContactPhone,
            isDark = true
        )
        Spacer(Modifier.height(12.dp))
        CustomTextField(
            value = priorityOrder,
            onValueChange = {
                if (it.isEmpty() || (it.length == 1 && it.all(Char::isDigit) && it in emergencyPriorityChoices)) {
                    priorityOrder = it
                }
            },
            placeholder = "Priority order",
            leadingIcon = Icons.Default.Phone,
            isDark = true
        )
        if (!priorityError.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(priorityError!!, color = Color(0xFFFFA726), fontSize = 12.sp)
        }
    }
}

@Composable
private fun OnboardingShell(
    title: String,
    subtitle: String,
    onSkip: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(OnboardingTop, OnboardingBottom)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Icon(
            imageVector = getSwiftAidIcon(),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(24.dp))
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color.LightGray, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        content()
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Skip")
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OnboardingBlue)
            ) {
                Text("Save")
            }
        }
    }
}
