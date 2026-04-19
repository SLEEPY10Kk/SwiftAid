package com.project.swiftaid

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Colors — matching the exact design in the photo
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
private val BackgroundTop     = Color(0xFF283B5A)
private val BackgroundBottom  = Color(0xFF1E212B)
private val FieldWhite        = Color(0xFFFFFFFF)
private val FieldText         = Color(0xFF000000)
private val TitleWhite        = Color(0xFFFFFFFF)
private val SubtitleColor     = Color.LightGray
private val ButtonBlue        = Color(0xFF336CFC)
private val ReportCardBg      = Color(0xFFF4F7FF)
private val ReportBorder      = Color(0xFFC5CFE8)
private val ReportLabelBlue   = Color(0xFF2563EB)
private val OptionalGray      = Color(0xFF9AA5C0)
private val LogoGradientStart = Color(0xFF007AFF)
private val LogoGradientEnd   = Color(0xFF0027A8)

// Blood group options
private val bloodGroups = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")

// Removed AppLogo and drawLogo here because they are exported from UserInfo.kt
// Screen
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
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

    var bloodGroupExpanded by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            s.reportUri  = it
            s.isReportAdded = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundTop, BackgroundBottom, BackgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.Start
        ) {
            GlobalLanguageSwitcher(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

            Icon(
                imageVector = getSwiftAidIcon(),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            // Heading
            Text(
                text = t.medicalInfoTitle,
                color = TitleWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = t.medicalInfoSubtitle,
                color = SubtitleColor,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            // ── 1. Blood Group Dropdown ───────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = bloodGroupExpanded,
                onExpandedChange = { bloodGroupExpanded = it }
            ) {
                BasicTextField(
                    value = s.bloodGroup,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(FieldWhite, RoundedCornerShape(12.dp))
                        .menuAnchor(),
                    textStyle = LocalTextStyle.current.copy(color = FieldText, fontSize = 16.sp),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (s.bloodGroup.isEmpty()) {
                                    Text(t.bloodGroup, color = Color.Gray, fontSize = 16.sp)
                                }
                                innerTextField()
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodGroupExpanded)
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = bloodGroupExpanded,
                    onDismissRequest = { bloodGroupExpanded = false }
                ) {
                    bloodGroups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group, fontSize = 14.sp) },
                            onClick = {
                                s.bloodGroup = group
                                bloodGroupExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = s.allergies,
                onValueChange = { s.allergies = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(FieldWhite, RoundedCornerShape(12.dp)),
                textStyle = LocalTextStyle.current.copy(color = FieldText, fontSize = 16.sp),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (s.allergies.isEmpty()) {
                                Text(t.allergies, color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                        Text(t.optional, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = s.chronicConditions,
                onValueChange = { s.chronicConditions = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .background(FieldWhite, RoundedCornerShape(12.dp)),
                textStyle = LocalTextStyle.current.copy(color = FieldText, fontSize = 16.sp),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (s.chronicConditions.isEmpty()) {
                                Text(t.chronicConditions, color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // ── 4. Current Medical Report (optional, file upload) ─────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FieldWhite)
                    .padding(14.dp)
            ) {
                // Card header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = ReportLabelBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = t.currentReport,
                        color = ReportLabelBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = t.optional,
                        color = OptionalGray,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Upload zone
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ReportCardBg)
                        .border(1.5.dp, ReportBorder, RoundedCornerShape(12.dp))
                        .clickable { filePicker.launch("*/*") }
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = ButtonBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (s.isReportAdded) "Health_Report.pdf" else t.tapToUpload,
                            color = if (s.isReportAdded) ReportLabelBlue else Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = t.supportedFormats,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ButtonBlue)
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
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue)
                ) {
                    Text(
                        text = t.nextBtn,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Preview
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Preview(showBackground = true, backgroundColor = 0xFF0B1437, widthDp = 360, heightDp = 820)
@Composable
fun MedicalInfoScreenPreview() {
    MaterialTheme {
        MedicalInfoScreen()
    }
}