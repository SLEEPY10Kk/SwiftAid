package com.example.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class PhoneCountryRule(
    val country: String,
    val dialCode: String,
    val minDigits: Int,
    val maxDigits: Int
)

data class PhoneParts(
    val country: String,
    val dialCode: String,
    val localNumber: String
)

val bloodGroupChoices = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

val phoneCountryRules = listOf(
    PhoneCountryRule("India", "+91", 10, 10),
    PhoneCountryRule("United States", "+1", 10, 10),
    PhoneCountryRule("Canada", "+1", 10, 10),
    PhoneCountryRule("United Kingdom", "+44", 10, 11),
    PhoneCountryRule("Australia", "+61", 9, 9),
    PhoneCountryRule("Germany", "+49", 10, 12),
    PhoneCountryRule("France", "+33", 9, 9),
    PhoneCountryRule("UAE", "+971", 8, 9),
    PhoneCountryRule("Singapore", "+65", 8, 8),
    PhoneCountryRule("South Africa", "+27", 9, 9)
)

val countryOptions = listOf(
    "India",
    "United States",
    "Canada",
    "United Kingdom",
    "Australia",
    "Germany",
    "France",
    "UAE",
    "Singapore",
    "South Africa"
)

fun stateOptionsForCountry(country: String): List<String> = when (country) {
    "India" -> listOf(
        "Andhra Pradesh",
        "Arunachal Pradesh",
        "Assam",
        "Bihar",
        "Chhattisgarh",
        "Delhi",
        "Goa",
        "Gujarat",
        "Haryana",
        "Karnataka",
        "Kerala",
        "Madhya Pradesh",
        "Maharashtra",
        "Punjab",
        "Rajasthan",
        "Tamil Nadu",
        "Telangana",
        "Uttar Pradesh",
        "West Bengal"
    )
    "United States" -> listOf(
        "Alabama",
        "California",
        "Florida",
        "Georgia",
        "Illinois",
        "New York",
        "Texas",
        "Washington"
    )
    "Canada" -> listOf(
        "Alberta",
        "British Columbia",
        "Manitoba",
        "Ontario",
        "Quebec",
        "Saskatchewan"
    )
    "United Kingdom" -> listOf("England", "Scotland", "Wales", "Northern Ireland")
    "Australia" -> listOf(
        "New South Wales",
        "Queensland",
        "South Australia",
        "Tasmania",
        "Victoria",
        "Western Australia"
    )
    else -> emptyList()
}

val insuranceTypeChoices = listOf(
    "Individual",
    "Family Floater",
    "Group",
    "Senior Citizen",
    "Critical Illness",
    "Accident Cover"
)

val insuranceProviderChoices = listOf(
    "LIC of India",
    "Star Health Insurance",
    "HDFC ERGO",
    "ICICI Lombard",
    "New India Assurance",
    "Bajaj Allianz",
    "Max Bupa",
    "Reliance General"
)

val emergencyPriorityChoices = listOf("1", "2", "3", "4", "5")

fun defaultPhoneParts(): PhoneParts = phoneCountryRules.first().let {
    PhoneParts(country = it.country, dialCode = it.dialCode, localNumber = "")
}

fun parsePhoneParts(value: String): PhoneParts {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return defaultPhoneParts()

    val code = Regex("^\\+(\\d{1,4})").find(trimmed)?.value ?: ""
    val rule = phoneCountryRules.firstOrNull { it.dialCode == code } ?: phoneCountryRules.first()
    val local = trimmed.removePrefix(code).trim().filter(Char::isDigit)
    return PhoneParts(country = rule.country, dialCode = rule.dialCode, localNumber = local)
}

fun formatPhoneNumber(dialCode: String, localNumber: String): String {
    val digits = localNumber.filter(Char::isDigit)
    return if (digits.isBlank()) dialCode.trim() else "$dialCode $digits"
}

fun validatePhoneParts(country: String, localNumber: String): String? {
    val rule = phoneCountryRules.firstOrNull { it.country == country } ?: phoneCountryRules.first()
    val digits = localNumber.filter(Char::isDigit)
    if (digits.isBlank()) return "Phone number is required"
    if (digits.length !in rule.minDigits..rule.maxDigits) {
        return "${rule.country} numbers must be ${rule.minDigits} to ${rule.maxDigits} digits"
    }
    return null
}

fun validateBloodGroup(value: String): String? {
    if (value.isBlank()) return "Blood group is required"
    if (value !in bloodGroupChoices) return "Select a valid blood group"
    return null
}

fun validateInsurancePolicyNumber(value: String): String? {
    val cleaned = value.trim()
    if (cleaned.isBlank()) return "Policy number is required"
    if (cleaned.length < 6) return "Policy number is too short"
    if (!cleaned.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        return "Use letters, digits, hyphen, or underscore only"
    }
    return null
}

fun validatePriorityOrder(value: String): String? {
    val cleaned = value.trim()
    if (cleaned.isBlank()) return "Priority order is required"
    val parsed = cleaned.toIntOrNull() ?: return "Priority order must be a number"
    if (parsed !in 1..5) return "Priority order must be between 1 and 5"
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Priority order",
    isDark: Boolean,
    error: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            CustomTextField(
                value = value,
                onValueChange = {},
                placeholder = placeholder,
                leadingIcon = Icons.Default.Favorite,
                isDark = isDark,
                modifier = Modifier.menuAnchor()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
            ) {
                emergencyPriorityChoices.forEach { option ->
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
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = error, color = Color(0xFFE74C3C), fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodGroupDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isDark: Boolean,
    error: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            CustomTextField(
                value = value,
                onValueChange = {},
                placeholder = placeholder,
                leadingIcon = Icons.Default.Favorite,
                isDark = isDark,
                modifier = Modifier.menuAnchor()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
            ) {
                bloodGroupChoices.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group, color = if (isDark) Color.White else Color.Black) },
                        onClick = {
                            onValueChange(group)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = error, color = Color(0xFFE74C3C), fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumberField(
    country: String,
    localNumber: String,
    onCountryChange: (String) -> Unit,
    onLocalNumberChange: (String) -> Unit,
    isDark: Boolean,
    placeholder: String = "Phone number",
    error: String? = null
) {
    val selectedRule = phoneCountryRules.firstOrNull { it.country == country } ?: phoneCountryRules.first()
    var expanded by remember(country) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1.2f)
            ) {
                CustomTextField(
                    value = country,
                    onValueChange = {},
                    placeholder = "Country",
                    leadingIcon = Icons.Default.Language,
                    isDark = isDark,
                    modifier = Modifier.menuAnchor()
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
                ) {
                    phoneCountryRules.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text("${option.country} (${option.dialCode})", color = if (isDark) Color.White else Color.Black)
                            },
                            onClick = {
                                onCountryChange(option.country)
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = selectedRule.dialCode,
                onValueChange = {},
                modifier = Modifier.width(92.dp),
                readOnly = true,
                singleLine = true,
                label = { Text("Code") }
            )
        }

        Spacer(Modifier.height(12.dp))

        CustomTextField(
            value = localNumber,
            onValueChange = { onLocalNumberChange(it.filter(Char::isDigit)) },
            placeholder = placeholder,
            leadingIcon = Icons.Default.Phone,
            isDark = isDark,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = error, color = Color(0xFFE74C3C), fontWeight = FontWeight.Medium)
        }
    }
}

fun normalizeDisplayPhone(value: String): String {
    val parts = parsePhoneParts(value)
    return if (parts.localNumber.isBlank()) value else formatPhoneNumber(parts.dialCode, parts.localNumber)
}
