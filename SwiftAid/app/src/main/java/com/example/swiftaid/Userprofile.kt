package com.example.swiftaid

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size

@Composable
fun UserProfileScreen(
    onBack: () -> Unit = {}
) {
    val sharedState = LocalSharedState.current
    val isDark = LocalIsDark.current
    val snapshot = sharedState.settingsSnapshot

    val displayName = remember(snapshot, sharedState.fullName, sharedState.username) {
        snapshot?.user?.fullName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(snapshot?.user?.firstName, snapshot?.user?.lastName).joinToString(" ").trim().ifBlank {
                sharedState.fullName.takeIf { it.isNotBlank() }
                    ?: sharedState.username.takeIf { it.isNotBlank() }
                    ?: "User"
            }
    }
    val initials = remember(displayName) { displayName.toInitials() }

    val background = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF08111F)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFEAF1FF), Color(0xFFF8FBFF)))
    }

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(bottom = 24.dp)
            ) {
                ProfileTopBar(onBack = onBack, isDark = isDark)

                ProfileHeroCard(
                    initials = initials,
                    name = displayName,
                    phone = snapshot?.user?.phoneNumber.orEmpty(),
                    percent = sharedState.safetyProfilePercent.coerceIn(0, 100),
                    isDark = isDark
                )

                Spacer(modifier = Modifier.height(18.dp))
                ProfileSection(
                    title = "Basic profile",
                    subtitle = "Identity and contact information",
                    isDark = isDark
                ) {
                    snapshot?.let {
                        BasicProfileRow("Username", it.basicFieldValue(BasicProfileField.Username), isDark)
                        DividerLine(isDark)
                        BasicProfileRow("Full name", it.basicFieldValue(BasicProfileField.FullName), isDark)
                        DividerLine(isDark)
                        BasicProfileRow("Age", it.user?.age?.toString().orEmpty().ifBlank { "Not set" }, isDark)
                        DividerLine(isDark)
                        BasicProfileRow("Gender", it.user?.gender.orEmpty().ifBlank { "Not set" }, isDark)
                        DividerLine(isDark)
                        BasicProfileRow("Phone", it.basicFieldValue(BasicProfileField.Phone), isDark)
                        DividerLine(isDark)
                        BasicProfileRow(
                            "Location",
                            listOfNotNull(it.user?.area, it.user?.city, it.user?.state, it.user?.country)
                                .joinToString(", ")
                                .ifBlank { "Not set" },
                            isDark
                        )
                    } ?: EmptyOverviewMessage("Profile data not loaded yet.", isDark)
                }

                Spacer(modifier = Modifier.height(14.dp))
                ProfileSection(
                    title = "Emergency contacts",
                    subtitle = "Trusted people for urgent calls",
                    isDark = isDark
                ) {
                    val contacts = snapshot?.emergencyContacts.orEmpty()
                    if (contacts.isEmpty()) {
                        EmptyOverviewMessage("No emergency contacts saved.", isDark)
                    } else {
                        contacts.forEachIndexed { index, contact ->
                            ContactCard(
                                name = contact.contactName.orEmpty().ifBlank { "Contact ${index + 1}" },
                                phone = contact.contactNumber,
                                relationship = contact.relationship,
                                priority = contact.priorityOrder,
                                isDark = isDark
                            )
                            if (index != contacts.lastIndex) Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                ProfileSection(
                    title = "Medical info",
                    subtitle = "Health details shared during emergencies",
                    isDark = isDark
                ) {
                    val medical = snapshot?.medicalInfo
                    if (medical == null) {
                        EmptyOverviewMessage("No medical information saved.", isDark)
                    } else {
                        OverviewRow("Blood group", medical.bloodGroup, Icons.Default.Favorite, isDark)
                        DividerLine(isDark)
                        OverviewRow("Allergies", medical.allergies.ifBlank { "Not set" }, Icons.Default.Shield, isDark)
                        DividerLine(isDark)
                        OverviewRow("Chronic conditions", medical.chronicConditions.ifBlank { "Not set" }, Icons.Default.Favorite, isDark)
                        DividerLine(isDark)
                        OverviewRow("Current medications", medical.currentmedications.ifBlank { "Not set" }, Icons.Default.Cake, isDark)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                ProfileSection(
                    title = "Insurance info",
                    subtitle = "Policy records linked to this account",
                    isDark = isDark
                ) {
                    val insurances = snapshot?.insuranceInfo.orEmpty()
                    if (insurances.isEmpty()) {
                        EmptyOverviewMessage("No insurance policies saved.", isDark)
                    } else {
                        insurances.forEachIndexed { index, insurance ->
                            InsuranceOverviewCard(insurance = insurance, index = index + 1, isDark = isDark)
                            if (index != insurances.lastIndex) Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back to Settings")
                }
            }
        }
    }
}

@Composable
private fun ProfileTopBar(onBack: () -> Unit, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Back",
                tint = if (isDark) Color.White else Color.Black
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "Complete User Profile",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black
            )
            Text(
                text = "Organized profile summary",
                fontSize = 13.sp,
                color = if (isDark) Color.White.copy(alpha = 0.68f) else Color.Gray
            )
        }
    }
}

@Composable
private fun ProfileHeroCard(
    initials: String,
    name: String,
    phone: String,
    percent: Int,
    isDark: Boolean
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF162033) else Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF4A7FF5), Color(0xFF3B82F6)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black)
                    Text(phone.ifBlank { "No phone number saved" }, fontSize = 13.sp, color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF4A7FF5).copy(alpha = 0.12f)) {
                    Text(
                        text = "$percent%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A7FF5)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = percent / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF4A7FF5),
                trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE6ECF5)
            )
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    subtitle: String,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            color = if (isDark) Color.White else Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = if (isDark) Color.White.copy(alpha = 0.68f) else Color.Gray,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF162033) else Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun BasicProfileRow(label: String, value: String, isDark: Boolean) {
    OverviewRow(label, value.ifBlank { "Not set" }, Icons.Default.Person, isDark)
}

@Composable
private fun OverviewRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF4A7FF5))
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(label, color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.SemiBold)
            Text(value, color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ContactCard(
    name: String,
    phone: String,
    relationship: String,
    priority: Int,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF8FAFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(name, color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(phone, color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            Text("Relationship: $relationship", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            Text("Priority: $priority", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InsuranceOverviewCard(
    insurance: InsuranceInfoResponse,
    index: Int,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF8FAFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Policy $index", color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(insurance.insuranceProvider, color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            Text("Type: ${insurance.insuranceType}", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            Text("Policy #: ${insurance.insurancePolicyNumber}", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            insurance.policyHolderName?.takeIf { it.isNotBlank() }?.let {
                Text("Holder: $it", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            }
            insurance.coverageType?.takeIf { it.isNotBlank() }?.let {
                Text("Coverage: $it", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            }
            insurance.expiryDate?.takeIf { it.isNotBlank() }?.let {
                Text("Expiry: $it", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            }
            insurance.coverageAmount?.takeIf { it.isNotBlank() }?.let {
                Text("Coverage amount: $it", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EmptyOverviewMessage(message: String, isDark: Boolean) {
    Text(
        text = message,
        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray,
        fontSize = 13.sp
    )
}

@Composable
private fun DividerLine(isDark: Boolean) {
    Divider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
    Spacer(modifier = Modifier.height(12.dp))
}

private fun String.toInitials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "U"
    val first = parts.first().firstOrNull()?.uppercaseChar()
    val second = parts.drop(1).firstOrNull()?.firstOrNull()?.uppercaseChar()
    return buildString {
        if (first != null) append(first)
        if (second != null) append(second)
    }.ifBlank { "U" }
}
