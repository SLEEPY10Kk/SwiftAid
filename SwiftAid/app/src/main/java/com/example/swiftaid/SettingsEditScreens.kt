package com.example.swiftaid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEditorSheet(
    editor: SettingsEditorState,
    onDismiss: () -> Unit,
    onSaveBasicField: (BasicProfileField, String) -> Unit,
    onSaveMedicalField: (MedicalField, String) -> Unit,
    onSaveEmergencyContact: (EmergencyContactResponse?, String, String, String, Int) -> Unit,
    onSaveInsurance: (InsuranceInfoResponse?, String, String, String, String, String, String, String, String) -> Unit,
    onDeleteEmergencyContact: (EmergencyContactResponse) -> Unit,
    onDeleteInsurance: (InsuranceInfoResponse) -> Unit,
    onMessage: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        when (editor) {
            is SettingsEditorState.BasicFieldEditor -> BasicFieldEditorContent(
                editor = editor,
                onDismiss = onDismiss,
                onSave = { value -> onSaveBasicField(editor.field, value) }
            )

            is SettingsEditorState.MedicalFieldEditor -> MedicalFieldEditorContent(
                editor = editor,
                onDismiss = onDismiss,
                onSave = { value -> onSaveMedicalField(editor.field, value) }
            )

            is SettingsEditorState.EmergencyContactEditor -> EmergencyContactEditorContent(
                editor = editor,
                onDismiss = onDismiss,
                onSave = onSaveEmergencyContact,
                onDelete = onDeleteEmergencyContact,
                onMessage = onMessage
            )

            is SettingsEditorState.InsuranceEditor -> InsuranceEditorContent(
                editor = editor,
                onDismiss = onDismiss,
                onSave = onSaveInsurance,
                onDelete = onDeleteInsurance
            )
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                Text(text = "Delete", modifier = Modifier.padding(start = 8.dp))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BasicFieldEditorContent(
    editor: SettingsEditorState.BasicFieldEditor,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(editor) { mutableStateOf(editor.currentValue) }
    val phoneParts = remember(editor.currentValue) { parsePhoneParts(editor.currentValue) }
    var phoneCountry by remember(editor.currentValue) { mutableStateOf(phoneParts.country) }
    var phoneLocal by remember(editor.currentValue) { mutableStateOf(phoneParts.localNumber) }
    var phoneError by remember(editor.currentValue) { mutableStateOf<String?>(null) }

    EditorSheetScaffold(
        title = editor.field.label,
        subtitle = "Update this profile field",
        onDismiss = onDismiss,
        onSave = {
            if (editor.field == BasicProfileField.Phone) {
                phoneError = validatePhoneParts(phoneCountry, phoneLocal)
                if (phoneError == null) {
                    onSave(
                        formatPhoneNumber(
                            phoneCountryRules.firstOrNull { it.country == phoneCountry }?.dialCode ?: "+91",
                            phoneLocal
                        )
                    )
                }
            } else {
                onSave(value.trim())
            }
        },
        saveEnabled = if (editor.field == BasicProfileField.Phone) {
            validatePhoneParts(phoneCountry, phoneLocal) == null
        } else {
            value.isNotBlank()
        }
    ) {
        if (editor.field == BasicProfileField.Phone) {
            PhoneNumberField(
                country = phoneCountry,
                localNumber = phoneLocal,
                onCountryChange = { phoneCountry = it },
                onLocalNumberChange = { phoneLocal = it },
                isDark = true,
                placeholder = editor.field.label,
                error = phoneError
            )
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(editor.field.label) },
                singleLine = true
            )
        }
    }
}

@Composable
private fun MedicalFieldEditorContent(
    editor: SettingsEditorState.MedicalFieldEditor,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(editor) { mutableStateOf(editor.currentValue) }
    var bloodError by remember(editor.currentValue) { mutableStateOf<String?>(null) }

    EditorSheetScaffold(
        title = editor.field.label,
        subtitle = "Update medical information for responders",
        onDismiss = onDismiss,
        onSave = {
            if (editor.field == MedicalField.BloodGroup) {
                bloodError = validateBloodGroup(value)
                if (bloodError == null) onSave(value.trim())
            } else {
                onSave(value.trim())
            }
        },
        saveEnabled = if (editor.field == MedicalField.BloodGroup) {
            validateBloodGroup(value) == null
        } else {
            value.isNotBlank()
        }
    ) {
        if (editor.field == MedicalField.BloodGroup) {
            BloodGroupDropdownField(
                value = value,
                onValueChange = { value = it },
                placeholder = editor.field.label,
                isDark = true,
                error = bloodError
            )
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(editor.field.label) },
                minLines = 2
            )
        }
    }
}

@Composable
private fun EmergencyContactEditorContent(
    editor: SettingsEditorState.EmergencyContactEditor,
    onDismiss: () -> Unit,
    onSave: (EmergencyContactResponse?, String, String, String, Int) -> Unit,
    onDelete: (EmergencyContactResponse) -> Unit,
    onMessage: (String) -> Unit
) {
    var contactName by remember(editor) { mutableStateOf(editor.contact?.contactName.orEmpty()) }
    var contactNumber by remember(editor.contact?.contactNumber) { mutableStateOf(editor.contact?.contactNumber.orEmpty()) }
    var relationship by remember(editor) { mutableStateOf(editor.contact?.relationship.orEmpty()) }
    var priority by remember(editor) { mutableStateOf(editor.contact?.priorityOrder?.toString().orEmpty()) }
    var priorityError by remember(editor) { mutableStateOf<String?>(null) }

    EditorSheetScaffold(
        title = if (editor.contact == null) "Add emergency contact" else "Edit emergency contact",
        subtitle = "Save the contact details used during emergencies",
        onDismiss = onDismiss,
        onSave = {
            priorityError = validatePriorityOrder(priority)
            if (contactNumber.isBlank() || priorityError != null) return@EditorSheetScaffold
            onSave(
                editor.contact,
                contactName.trim(),
                contactNumber,
                relationship.trim(),
                priority.toIntOrNull() ?: 1
            )
        },
        saveEnabled = contactNumber.isNotBlank() &&
            relationship.isNotBlank() &&
            validatePriorityOrder(priority) == null
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
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = contactName,
            onValueChange = { contactName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contact name") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = contactNumber,
            onValueChange = { contactNumber = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contact number") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = relationship,
            onValueChange = { relationship = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Relationship") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        PriorityDropdownField(
            value = priority,
            onValueChange = { priority = it },
            placeholder = "Priority order",
            isDark = true,
            error = priorityError
        )
    }
}

@Composable
private fun InsuranceEditorContent(
    editor: SettingsEditorState.InsuranceEditor,
    onDismiss: () -> Unit,
    onSave: (InsuranceInfoResponse?, String, String, String, String, String, String, String, String) -> Unit,
    onDelete: (InsuranceInfoResponse) -> Unit
) {
    var insuranceType by remember(editor) { mutableStateOf(editor.insurance?.insuranceType.orEmpty()) }
    var provider by remember(editor) { mutableStateOf(editor.insurance?.insuranceProvider.orEmpty()) }
    var policyNumber by remember(editor) { mutableStateOf(editor.insurance?.insurancePolicyNumber.orEmpty()) }
    var policyHolderName by remember(editor) { mutableStateOf(editor.insurance?.policyHolderName.orEmpty()) }
    var coverageType by remember(editor) { mutableStateOf(editor.insurance?.coverageType.orEmpty()) }
    var expiryDate by remember(editor) { mutableStateOf(editor.insurance?.expiryDate.orEmpty()) }
    var coverageAmount by remember(editor) { mutableStateOf(editor.insurance?.coverageAmount.orEmpty()) }
    var documentUri by remember(editor) { mutableStateOf(editor.insurance?.documentUri.orEmpty()) }
    var policyError by remember(editor) { mutableStateOf<String?>(null) }

    EditorSheetScaffold(
        title = if (editor.insurance == null) "Add insurance info" else "Edit insurance info",
        subtitle = "Save the insurance details used for claims",
        onDismiss = onDismiss,
        onSave = {
            policyError = validateInsurancePolicyNumber(policyNumber)
            if (policyError != null) return@EditorSheetScaffold
            onSave(
                editor.insurance,
                insuranceType.trim(),
                provider.trim(),
                policyNumber.trim(),
                policyHolderName.trim(),
                coverageType.trim(),
                expiryDate.trim(),
                coverageAmount.trim(),
                documentUri.trim()
            )
        },
        saveEnabled = insuranceType.isNotBlank() && provider.isNotBlank() && validateInsurancePolicyNumber(policyNumber) == null
    ) {
        DropdownField(
            value = insuranceType,
            placeholder = "Insurance type",
            options = insuranceTypeChoices,
            isDark = true,
            onValueChange = { insuranceType = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        DropdownField(
            value = provider,
            placeholder = "Provider",
            options = insuranceProviderChoices,
            isDark = true,
            onValueChange = { provider = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = policyNumber,
            onValueChange = { policyNumber = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Policy number") },
            singleLine = true
        )
        if (!policyError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(policyError!!, color = Color(0xFFE74C3C), fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = policyHolderName,
            onValueChange = { policyHolderName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Policy holder name") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = coverageType,
            onValueChange = { coverageType = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Coverage type") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = expiryDate,
            onValueChange = { expiryDate = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Expiry date") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = coverageAmount,
            onValueChange = { coverageAmount = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Coverage amount") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = documentUri,
            onValueChange = { documentUri = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Document URI") },
            singleLine = true
        )

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    value: String,
    placeholder: String,
    options: List<String>,
    isDark: Boolean,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        CustomTextField(
            value = value,
            onValueChange = {},
            placeholder = placeholder,
            leadingIcon = Icons.Default.Edit,
            isDark = isDark,
            modifier = Modifier.menuAnchor()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = if (isDark) Color.White else Color.Black) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheetScaffold(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        content()
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                Text(text = "Save", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
