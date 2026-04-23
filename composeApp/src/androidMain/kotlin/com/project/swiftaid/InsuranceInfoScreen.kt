package com.project.swiftaid

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dropdown options
private val insuranceProviders = listOf(
    "LIC of India", "Star Health Insurance", "HDFC ERGO",
    "ICICI Lombard", "New India Assurance", "Bajaj Allianz",
    "Max Bupa", "Reliance General"
)

private val coverageTypes = listOf(
    "Individual", "Family Floater", "Group",
    "Senior Citizen", "Critical Illness", "Accident Cover"
)

@Composable
fun InsuranceInfoScreen(
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val t = Translations.get(LocalLanguage.current)
    val s = LocalSharedState.current
    val isDark = LocalIsDark.current
    val insurances = s.insurances
    var expandedIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF3683FF),
                            Color(0xFF000000)
                        ),
                        startY = 0f,
                        endY = 900f
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF3B82F6),
                            Color(0xFFFFFFFF)
                        ),
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

            // Shield Logo
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
                    text = "Insurance Details",
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Add up to 5 insurance policies",
                    color = if (isDark) Color.LightGray else Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(24.dp))

                // ── Insurance List ──────────────────────────────────────────
                insurances.forEachIndexed { index, insurance ->
                    InsuranceAccordionItem(
                        insurance = insurance,
                        isExpanded = expandedIndex == index,
                        isDark = isDark,
                        onToggle = { expandedIndex = if (expandedIndex == index) -1 else index },
                        onDelete = {
                            insurances.removeAt(index)
                            if (expandedIndex == index) expandedIndex = -1
                            else if (expandedIndex > index) expandedIndex--
                        },
                        onUpdate = { updated -> insurances[index] = updated }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // ── Add Button ──────────────────────────────────────────────
                if (insurances.size < 5) {
                    Button(
                        onClick = {
                            insurances.add(Insurance())
                            expandedIndex = insurances.size - 1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                            contentColor = if (isDark) Color.White else Color.Black
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = if (isDark) Color.White else Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("ADD INSURANCE", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // ── Navigation Buttons ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) Color.White else Color.Black
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(text = t.backBtn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onContinue,
                        enabled = insurances.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                            contentColor = if (isDark) Color.Black else Color.White
                        )
                    ) {
                        Text(text = t.nextBtn, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsuranceAccordionItem(
    insurance: Insurance,
    isExpanded: Boolean,
    isDark: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (Insurance) -> Unit
) {
    val buttonBlue = Color(0xFF4A7FF5)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
            } else {
                if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.02f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isExpanded) buttonBlue.copy(alpha = 0.5f) else Color.Transparent
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onToggle() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = if (isExpanded) buttonBlue else (if (isDark) Color.White else Color.Black),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = insurance.provider.ifEmpty { "New Insurance" },
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    val t = Translations.get(LocalLanguage.current)
                    var providerExpanded by remember { mutableStateOf(false) }
                    var coverageTypeExpanded by remember { mutableStateOf(false) }
                    var showDatePicker by remember { mutableStateOf(false) }
                    val datePickerState = rememberDatePickerState()

                    if (showDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                                        val date = "%02d-%02d-%04d".format(
                                            cal.get(java.util.Calendar.DAY_OF_MONTH),
                                            cal.get(java.util.Calendar.MONTH) + 1,
                                            cal.get(java.util.Calendar.YEAR)
                                        )
                                        onUpdate(insurance.copy(expiryDate = date))
                                    }
                                    showDatePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                            }
                        ) { DatePicker(state = datePickerState) }
                    }

                    val filePicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent()
                    ) { uri ->
                        onUpdate(insurance.copy(documentUri = uri))
                    }

                    // Provider Dropdown
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = it }
                    ) {
                        CustomTextField(
                            value = insurance.provider,
                            onValueChange = {},
                            placeholder = "Insurance Provider",
                            leadingIcon = Icons.Outlined.Home,
                            isDark = isDark,
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false },
                            modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
                        ) {
                            insuranceProviders.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item, color = if (isDark) Color.White else Color.Black) },
                                    onClick = {
                                        onUpdate(insurance.copy(provider = item))
                                        providerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    CustomTextField(
                        value = insurance.policyNumber,
                        onValueChange = { onUpdate(insurance.copy(policyNumber = it)) },
                        placeholder = "Policy Number",
                        leadingIcon = Icons.Outlined.CreditCard,
                        isDark = isDark
                    )

                    Spacer(Modifier.height(12.dp))

                    CustomTextField(
                        value = insurance.policyHolderName,
                        onValueChange = { onUpdate(insurance.copy(policyHolderName = it)) },
                        placeholder = "Policy Holder Name",
                        leadingIcon = Icons.Outlined.Person,
                        isDark = isDark
                    )

                    Spacer(Modifier.height(12.dp))

                    // Coverage Type
                    ExposedDropdownMenuBox(
                        expanded = coverageTypeExpanded,
                        onExpandedChange = { coverageTypeExpanded = it }
                    ) {
                        CustomTextField(
                            value = insurance.coverageType,
                            onValueChange = {},
                            placeholder = "Coverage Type",
                            leadingIcon = Icons.Outlined.Shield,
                            isDark = isDark,
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = coverageTypeExpanded,
                            onDismissRequest = { coverageTypeExpanded = false },
                            modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
                        ) {
                            coverageTypes.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item, color = if (isDark) Color.White else Color.Black) },
                                    onClick = {
                                        onUpdate(insurance.copy(coverageType = item))
                                        coverageTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Expiry Date
                    Box(modifier = Modifier.clickable { showDatePicker = true }) {
                        CustomTextField(
                            value = insurance.expiryDate,
                            onValueChange = {},
                            placeholder = "Expiry Date (dd-mm-yyyy)",
                            leadingIcon = Icons.Outlined.CalendarMonth,
                            isDark = isDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    CustomTextField(
                        value = insurance.coverageAmount,
                        onValueChange = { onUpdate(insurance.copy(coverageAmount = it)) },
                        placeholder = "Coverage Amount (₹)",
                        leadingIcon = Icons.Outlined.CurrencyRupee,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isDark = isDark
                    )

                    Spacer(Modifier.height(16.dp))

                    // Upload Document
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { filePicker.launch("*/*") }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = buttonBlue)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (insurance.documentUri == null) "Upload Document" else "Document Added",
                                color = buttonBlue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun InsuranceInfoScreenPreview() {
    InsuranceInfoScreen()
}
