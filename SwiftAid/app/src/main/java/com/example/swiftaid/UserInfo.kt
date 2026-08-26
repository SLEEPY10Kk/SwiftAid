package com.example.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon

@Composable
fun CreateAccountScreen(
    onLanguageChange: (String) -> Unit = {},
    onCreateAccount: (
        username: String,
        fullName: String,
        age: String,
        gender: String,
        phone: String,
        password: String,
        city: String,
        state: String,
        country: String,
        exactArea: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onBack: () -> Unit = {}
) {
    val t = Translations.get(LocalLanguage.current)
    val s = LocalSharedState.current
    val isDark = LocalIsDark.current

    var showPass by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var age by remember { mutableStateOf(s.age) }
    var gender by remember { mutableStateOf(s.gender) }
    var genderExpanded by remember { mutableStateOf(false) }
    var phoneCountry by remember { mutableStateOf(defaultPhoneParts().country) }
    var localPhone by remember { mutableStateOf("") }
    var ageError by remember { mutableStateOf<String?>(null) }
    var genderError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF3683FF), Color(0xFF000000)),
                        startY = 0f,
                        endY = 900f
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF3B82F6), Color(0xFFFFFFFF)),
                        startY = 0f,
                        endY = 1000f
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBarToggles()
            Spacer(modifier = Modifier.height(16.dp))

            Icon(
                imageVector = getSwiftAidIcon(),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(56.dp)
            )

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = t.createAccountTitle,
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = t.createAccountSubtitle,
                    color = if (isDark) Color.LightGray else Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(24.dp))

                CustomTextField(
                    value = s.username,
                    onValueChange = { s.username = it },
                    placeholder = t.username,
                    leadingIcon = Icons.Default.Person,
                    isDark = isDark
                )
                Spacer(Modifier.height(12.dp))

                CustomTextField(
                    value = s.fullName,
                    onValueChange = { s.fullName = it },
                    placeholder = t.fullName,
                    leadingIcon = Icons.Default.AccountCircle,
                    isDark = isDark
                )
                Spacer(Modifier.height(12.dp))

                DropdownField(
                    value = gender,
                    placeholder = "Gender",
                    options = genderOptions,
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = it },
                    isDark = isDark,
                    onSelected = {
                        gender = it
                        s.gender = it
                        genderError = null
                    },
                    leadingIcon = Icons.Default.Person
                )
                if (!genderError.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = genderError!!, color = Color(0xFFE74C3C), fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))

                CustomTextField(
                    value = age,
                    onValueChange = {
                        val normalized = it.filter(Char::isDigit).take(3)
                        age = normalized
                        s.age = normalized
                        ageError = null
                    },
                    placeholder = "Age",
                    leadingIcon = Icons.Default.CalendarMonth,
                    isDark = isDark,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (!ageError.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = ageError!!, color = Color(0xFFE74C3C), fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))

                PhoneNumberField(
                    country = phoneCountry,
                    localNumber = localPhone,
                    onCountryChange = { phoneCountry = it },
                    onLocalNumberChange = { localPhone = it },
                    isDark = isDark,
                    placeholder = t.phoneNumber,
                    error = phoneError
                )
                Spacer(Modifier.height(12.dp))

                CustomTextField(
                    value = s.password,
                    onValueChange = { s.password = it },
                    placeholder = t.password,
                    isPassword = true,
                    passwordVisible = showPass,
                    onPasswordToggle = { showPass = !showPass },
                    leadingIcon = Icons.Default.Lock,
                    isDark = isDark
                )
                Spacer(Modifier.height(24.dp))

                AddressSection(
                    city = s.city, onCityChange = { s.city = it },
                    state = s.state, onStateChange = { s.state = it },
                    country = s.country, onCountryChange = { s.country = it },
                    exactArea = s.exactArea, onExactAreaChange = { s.exactArea = it },
                    isDark = isDark
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreedToTerms = !agreedToTerms },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF3B82F6),
                            uncheckedColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray,
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = "I agree to the ",
                        color = if (isDark) Color.LightGray else Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Terms & Privacy Policy",
                        color = Color(0xFF3B82F6),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { TermsState.isVisible = true }
                    )
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) Color.White else Color.Black
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = t.backBtn,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            ageError = validateAge(age)
                            genderError = if (gender.isBlank()) "Gender is required" else null
                            phoneError = validatePhoneParts(phoneCountry, localPhone)
                            val formattedPhone = formatPhoneNumber(
                                phoneCountryRules.firstOrNull { it.country == phoneCountry }?.dialCode ?: "+91",
                                localPhone
                            )
                            if (ageError != null || genderError != null || phoneError != null) return@Button
                            onCreateAccount(
                                s.username,
                                s.fullName,
                                age,
                                gender,
                                formattedPhone,
                                s.password,
                                s.city,
                                s.state,
                                s.country,
                                s.exactArea
                            )
                        },
                        enabled = agreedToTerms
                            && s.username.isNotBlank()
                            && s.fullName.isNotBlank()
                            && validateAge(age) == null
                            && gender.isNotBlank()
                            && s.password.isNotBlank()
                            && s.city.isNotBlank()
                            && s.state.isNotBlank()
                            && s.country.isNotBlank()
                            && s.exactArea.isNotBlank()
                            && validatePhoneParts(phoneCountry, localPhone) == null,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                            contentColor = if (isDark) Color.Black else Color.White,
                            disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f),
                            disabledContentColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = t.nextBtn,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddressSection(
    city: String, onCityChange: (String) -> Unit,
    state: String, onStateChange: (String) -> Unit,
    country: String, onCountryChange: (String) -> Unit,
    exactArea: String, onExactAreaChange: (String) -> Unit,
    isDark: Boolean
) {
    val t = Translations.get(LocalLanguage.current)
    var countryExpanded by remember { mutableStateOf(false) }
    var stateExpanded by remember(country) { mutableStateOf(false) }
    val stateOptions = remember(country) { stateOptionsForCountry(country) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFF4A7FF5),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = t.addressTitle.uppercase(),
                color = Color(0xFF4A7FF5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                DropdownField(
                    value = country,
                    placeholder = t.country,
                    options = countryOptions,
                    expanded = countryExpanded,
                    onExpandedChange = { countryExpanded = it },
                    isDark = isDark,
                    onSelected = { selectedCountry ->
                        onCountryChange(selectedCountry)
                        val validStates = stateOptionsForCountry(selectedCountry)
                        if (state !in validStates) {
                            onStateChange("")
                        }
                    }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DropdownField(
                    value = state,
                    placeholder = t.state,
                    options = stateOptions,
                    expanded = stateExpanded,
                    onExpandedChange = { stateExpanded = it },
                    isDark = isDark,
                    onSelected = onStateChange,
                    enabled = stateOptions.isNotEmpty()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        CustomTextField(
            value = city,
            onValueChange = onCityChange,
            placeholder = t.city,
            isDark = isDark
        )

        Spacer(Modifier.height(12.dp))

        CustomTextField(
            value = exactArea,
            onValueChange = onExactAreaChange,
            placeholder = t.exactLocation,
            isDark = isDark,
            minHeight = 100.dp,
            maxLines = 4
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    value: String,
    placeholder: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isDark: Boolean,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
    leadingIcon: ImageVector = Icons.Default.Person
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) }
    ) {
        CustomTextField(
            value = value,
            onValueChange = {},
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            isDark = isDark,
            modifier = Modifier.menuAnchor()
        )
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = if (isDark) Color.White else Color.Black) },
                    onClick = {
                        onSelected(option)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 820)
@Composable
fun CreateAccountScreenPreview() {
    CreateAccountScreen()
}

val genderOptions = listOf("Male", "Female", "Non-binary", "Prefer not to say", "Other")

fun validateAge(value: String): String? {
    val cleaned = value.trim()
    if (cleaned.isBlank()) return "Age is required"
    val age = cleaned.toIntOrNull() ?: return "Age must be a number"
    if (age !in 1..120) return "Age must be between 1 and 120"
    return null
}
