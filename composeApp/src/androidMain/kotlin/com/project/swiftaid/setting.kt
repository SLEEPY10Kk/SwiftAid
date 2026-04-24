package com.project.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Brand Colors ────────────────────────────────────────────────────────────
private val SosBlueBrand    = Color(0xFF3B82F6) // Matches gradient start
private val SosDarkBlueBrand = Color(0xFF4A7FF5) // Matches button/remember color
private val SosOrange     = Color(0xFFE67E22)
private val SosBlue       = Color(0xFF2980B9)
private val SosGreen      = Color(0xFF27AE60)
private val SosPurple     = Color(0xFF8E44AD)

private val BlueBrandLight  = Color(0xFF3B82F6).copy(alpha = 0.1f)
private val OrangeLight   = Color(0xFFFFF3E0)
private val BlueLight     = Color(0xFFE8F4FF)
private val GreenLight    = Color(0xFFE8F8F0)
private val PurpleLight   = Color(0xFFF0EAFF)
private val GrayLight     = Color(0xFFF4F4F4)

private val BackgroundPage = Color(0xFFF2F2F7)
private val CardBgLight    = Color(0xFFFFFFFF)
private val CardBgDark     = Color(0xFF1A1A1A) // Matches your button color
private val DividerColor   = Color(0xFFF0F0F0)
private val TextPrimaryLight = Color(0xFF1A1A1A)
private val TextPrimaryDark  = Color(0xFFFFFFFF)
private val TextSecondary  = Color(0xFF888888)

data class SettingsRow(
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val title: String,
    val subtitle: String? = null,
    val hasToggle: Boolean = false,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit = {}
)

data class SettingsSection(
    val label: String,
    val rows: List<SettingsRow>
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var locationEnabled       by remember { mutableStateOf(true) }
    
    val currentLangCode = LocalLanguage.current
    val onLanguageChange = LocalLanguageChange.current
    val themeMode = LocalThemeMode.current
    val onThemeChange = LocalThemeChange.current
    val sharedState = LocalSharedState.current

    val currentLanguageName = SupportedLanguages.find { it.code == currentLangCode }?.name ?: "English"

    val sections = listOf(
        SettingsSection(
            label = "ACCOUNT",
            rows = listOf(
                SettingsRow(Icons.Outlined.Person, BlueBrandLight, SosBlueBrand, "Profile", "Name, Address, Medical Report"),
                SettingsRow(Icons.Outlined.Phone, BlueBrandLight, SosBlueBrand, "Emergency Contact", "1 contact saved")
            )
        ),
        SettingsSection(
            label = "PREFERENCES",
            rows = listOf(
                SettingsRow(Icons.Outlined.Notifications, BlueLight, SosBlue, "Notifications", "SOS alerts", true),
                SettingsRow(Icons.Outlined.LocationOn, GreenLight, SosGreen, "Location Services", "Always on", true),
                SettingsRow(Icons.Outlined.DarkMode, PurpleLight, SosPurple, "Appearance", themeMode.replaceFirstChar { it.uppercase() }),
                SettingsRow(Icons.Outlined.Language, OrangeLight, SosOrange, "Language", currentLanguageName)
            )
        ),
        SettingsSection(
            label = "SUPPORT",
            rows = listOf(
                SettingsRow(Icons.Outlined.Info, GrayLight, Color.Gray, "About SwiftAid", "v1.0.0"),
                SettingsRow(Icons.Outlined.Logout, BlueBrandLight, SosBlueBrand, "Sign Out", isDestructive = true, onClick = onSignOut)
            )
        )
    )

    val toggleMap = mapOf(
        "Notifications"     to (notificationsEnabled to { notificationsEnabled = !notificationsEnabled }),
        "Location Services" to (locationEnabled       to { locationEnabled       = !locationEnabled })
    )

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showMedicalReportPopUp by remember { mutableStateOf(false) }
    var showEmergencyContactPopUp by remember { mutableStateOf(false) }

    val isDark = LocalIsDark.current
    val dynamicBackground = if (isDark) {
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

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(dynamicBackground)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(paddingValues)
            ) {
                // TopBar without gradient background, just transparent Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 18.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))) {
                        Icon(Icons.Default.ChevronLeft, "Back", tint = if (isDark) Color.White else Color.Black)
                    }
                    Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black, modifier = Modifier.padding(start = 12.dp))
                }

                ProfileCard(modifier = Modifier.padding(16.dp))
                SosBanner(modifier = Modifier.padding(horizontal = 16.dp))
                
                sections.forEach { section ->
                    SettingsSectionBlock(
                        section = section,
                        toggleMap = toggleMap,
                        onRowClick = { row ->
                            when (row.title) {
                                "Language" -> showLanguageDialog = true
                                "Appearance" -> showThemeDialog = true
                                "Profile" -> showMedicalReportPopUp = true
                                "Emergency Contact" -> showEmergencyContactPopUp = true
                                else -> row.onClick()
                            }
                        },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showEmergencyContactPopUp) {
        AlertDialog(
            onDismissRequest = { showEmergencyContactPopUp = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, tint = SosBlueBrand)
                    Spacer(Modifier.width(8.dp))
                    Text("Emergency Contact")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Primary Contact", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (isDark) Color.LightGray else Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(SosBlueBrand), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Emergency Responder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("+91 98765 43210", color = TextSecondary, fontSize = 13.sp)
                        }
                        IconButton(onClick = { /* Call action */ }) {
                            Icon(Icons.Default.Call, null, tint = SosGreen)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("This contact will be notified immediately when you trigger an SOS alert.", fontSize = 12.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showEmergencyContactPopUp = false },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color.White else Color(0xFF1A1A1A))
                ) {
                    Text("Close", color = if (isDark) Color.Black else Color.White)
                }
            }
        )
    }

    if (showMedicalReportPopUp) {
        AlertDialog(
            onDismissRequest = { showMedicalReportPopUp = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MedicalServices, null, tint = SosBlueBrand)
                    Spacer(Modifier.width(8.dp))
                    Text("Medical Profile")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(SosBlueBrand.copy(0.1f)), contentAlignment = Alignment.Center) {
                            Text(sharedState.fullName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = SosBlueBrand)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(sharedState.fullName.ifEmpty { "User Name" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(sharedState.phone.ifEmpty { "+91 00000 00000" }, color = TextSecondary, fontSize = 14.sp)
                            val fullAddress = listOfNotNull(
                                sharedState.exactArea.ifEmpty { null },
                                sharedState.city.ifEmpty { null },
                                sharedState.state.ifEmpty { null }
                            ).joinToString(", ")
                            if (fullAddress.isNotEmpty()) {
                                Text(fullAddress, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DividerColor)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (sharedState.isReportAdded) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (sharedState.isReportAdded) SosGreen else SosOrange
                        )
                        Text(
                            text = if (sharedState.isReportAdded) "Medical Report Uploaded" else "Medical Report Not Found",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!sharedState.isReportAdded) {
                        Text(
                            "Please upload your medical history for better emergency assistance.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showMedicalReportPopUp = false },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color.White else Color(0xFF1A1A1A))
                ) {
                    Text("Close", color = if (isDark) Color.Black else Color.White)
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SupportedLanguages.forEach { lang ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onLanguageChange(lang.code)
                                showLanguageDialog = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = lang.code == currentLangCode,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = SosDarkBlueBrand)
                            )
                            Text(lang.name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel", color = SosDarkBlueBrand) } }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    listOf("system", "light", "dark").forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onThemeChange(mode)
                                showThemeDialog = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = SosDarkBlueBrand)
                            )
                            Text(mode.replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Cancel", color = SosDarkBlueBrand) } }
        )
    }
}

@Composable
private fun TopBarGradient(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(SosDarkBlueBrand, SosBlueBrand))).padding(top = 48.dp, bottom = 18.dp).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                Icon(Icons.Default.ChevronLeft, "Back", tint = Color.White)
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun ProfileCard(modifier: Modifier = Modifier) {
    val s = LocalSharedState.current
    val isDark = LocalIsDark.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) CardBgDark else CardBgLight)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SosBlueBrand, SosDarkBlueBrand)))) {
                Text(s.fullName.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(s.fullName.ifEmpty { "User Name" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) TextPrimaryDark else TextPrimaryLight)
                Text(s.phone.ifEmpty { "+91 00000 00000" }, fontSize = 13.sp, color = TextSecondary)
                if (s.city.isNotEmpty() || s.state.isNotEmpty()) {
                    Text("${s.city}, ${s.state}", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SosBanner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(SosDarkBlueBrand, SosBlueBrand))).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Emergency SOS", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Tap here to trigger emergency alert", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Button(
                onClick = { /* Trigger SOS */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SOS", color = SosBlueBrand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsSectionBlock(
    section: SettingsSection,
    toggleMap: Map<String, Pair<Boolean, () -> Unit>>,
    onRowClick: (SettingsRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDark.current
    Column(modifier = modifier) {
        Text(section.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.LightGray else Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) CardBgDark else CardBgLight)
        ) {
            Column {
                section.rows.forEachIndexed { index, row ->
                    SettingsRowItem(
                        row = row.copy(onClick = { onRowClick(row) }),
                        toggleState = toggleMap[row.title]?.first,
                        onToggle = toggleMap[row.title]?.second
                    )
                    if (index < section.rows.lastIndex) HorizontalDivider(color = DividerColor.copy(alpha = if (isDark) 0.1f else 1f), modifier = Modifier.padding(start = 62.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsRowItem(row: SettingsRow, toggleState: Boolean?, onToggle: (() -> Unit)?) {
    val isDark = LocalIsDark.current
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !row.hasToggle) { row.onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(row.iconBg)) {
            Icon(row.icon, null, tint = row.iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (row.isDestructive) SosDarkBlueBrand else (if (isDark) TextPrimaryDark else TextPrimaryLight))
            row.subtitle?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
        }
        if (row.hasToggle && toggleState != null && onToggle != null) {
            Switch(
                checked = toggleState,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SosDarkBlueBrand,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = if (isDark) Color.DarkGray else Color.LightGray,
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        } else if (!row.isDestructive) {
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFC7C7CC), modifier = Modifier.size(18.dp))
        }
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    val sharedState = remember { AppSharedState() }
    CompositionLocalProvider(LocalSharedState provides sharedState) {
        SettingsScreen()
    }
}
