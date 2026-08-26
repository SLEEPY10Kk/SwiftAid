package com.example.swiftaid

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bloodGroups = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalInfoScreen(
    onSaveAndContinue: (
        bloodGroup: String,
        allergies: String,
        chronicConditions: String,
        reportAdded: Boolean
    ) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit = {}
) {
    val t = Translations.get(LocalLanguage.current)
    val s = LocalSharedState.current
    val isDark = LocalIsDark.current

    var bloodGroupExpanded by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            s.reportUri = it
            s.isReportAdded = true
        }
    }

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
                    text = t.medicalInfoTitle,
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = t.medicalInfoSubtitle,
                    color = if (isDark) Color.LightGray else Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(24.dp))

                // Blood Group Dropdown
                ExposedDropdownMenuBox(
                    expanded = bloodGroupExpanded,
                    onExpandedChange = { bloodGroupExpanded = it }
                ) {
                    CustomTextField(
                        value = s.bloodGroup,
                        onValueChange = {},
                        placeholder = t.bloodGroup,
                        leadingIcon = Icons.Default.Favorite,
                        isDark = isDark,
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = bloodGroupExpanded,
                        onDismissRequest = { bloodGroupExpanded = false },
                        modifier = Modifier.background(if (isDark) Color(0xFF1E212B) else Color.White)
                    ) {
                        bloodGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = group, 
                                        color = if (isDark) Color.White else Color.Black 
                                    ) 
                                },
                                onClick = {
                                    s.bloodGroup = group
                                    bloodGroupExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                CustomTextField(
                    value = s.allergies,
                    onValueChange = { s.allergies = it },
                    placeholder = t.allergies,
                    leadingIcon = Icons.Default.Warning,
                    trailingText = t.optional,
                    isDark = isDark
                )

                Spacer(Modifier.height(12.dp))

                CustomTextField(
                    value = s.chronicConditions,
                    onValueChange = { s.chronicConditions = it },
                    placeholder = t.chronicConditions,
                    leadingIcon = Icons.Default.Favorite,
                    isDark = isDark,
                    minHeight = 100.dp,
                    maxLines = 4
                )

                Spacer(Modifier.height(24.dp))

                // Current Medical Report (optional, file upload)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF4A7FF5),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = t.currentReport,
                            color = Color(0xFF4A7FF5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = t.optional,
                            color = if (isDark) Color.Gray else Color.DarkGray,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                            .clickable { filePicker.launch("*/*") }
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF4A7FF5),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (s.isReportAdded) "Health_Report.pdf" else t.tapToUpload,
                                color = if (s.isReportAdded) Color(0xFF4A7FF5) else (if (isDark) Color.LightGray else Color.Gray),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = t.supportedFormats,
                                color = if (isDark) Color.Gray else Color.DarkGray,
                                fontSize = 11.sp
                            )
                        }
                    }
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
                            onSaveAndContinue(
                                s.bloodGroup,
                                s.allergies,
                                s.chronicConditions,
                                s.isReportAdded
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                            contentColor = if (isDark) Color.Black else Color.White
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

@Preview(showBackground = true, widthDp = 360, heightDp = 820)
@Composable
fun MedicalInfoScreenPreview() {
    MaterialTheme {
        MedicalInfoScreen()
    }
}
