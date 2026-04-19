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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
private val BackgroundDark    = Color(0xFF0B1437)
private val FieldWhite        = Color(0xFFFFFFFF)
private val FieldPlaceholder  = Color(0xFF9AA5C0)
private val FieldText         = Color(0xFF1A1A2E)
private val TitleWhite        = Color(0xFFFFFFFF)
private val SubtitleColor     = Color(0xFF7A8AB0)
private val ButtonBlue        = Color(0xFF2563EB)
private val ReportCardBg      = Color(0xFFF4F7FF)
private val ReportBorder      = Color(0xFFC5CFE8)
private val ReportLabelBlue   = Color(0xFF2563EB)
private val OptionalGray      = Color(0xFF9AA5C0)
private val LogoGradientStart = Color(0xFF007AFF)
private val LogoGradientEnd   = Color(0xFF0027A8)

// Blood group options
private val bloodGroups = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Logo — drawn via Compose Canvas (no XML resource needed)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
private fun AppLogo(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).drawBehind { drawLogo() })
}

private fun DrawScope.drawLogo() {
    val vb = 1024f
    val sx = size.width / vb
    val sy = size.height / vb

    // Gradient background
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(LogoGradientStart, LogoGradientEnd),
            start  = Offset(vb * 0.10f * sx, 0f),
            end    = Offset(vb * 0.90f * sx, vb * sy)
        ),
        size         = Size(size.width, size.height),
        cornerRadius = CornerRadius(200f * sx, 200f * sy)
    )

    fun s(x: Float) = x * sx
    fun t(y: Float) = y * sy

    // Main icon body
    val body = Path().apply {
        moveTo(s(432f), t(170f))
        arcTo(Rect(s(432f), t(90f), s(592f), t(250f)), 180f, -180f, false)
        lineTo(s(592f), t(380f))
        lineTo(s(760f), t(380f))
        arcTo(Rect(s(680f), t(380f), s(840f), t(540f)), 270f, 180f, false)
        lineTo(s(592f), t(540f))
        lineTo(s(800f), t(830f))
        arcTo(Rect(s(740f), t(800f), s(800f), t(860f)), 0f, 90f, false)
        lineTo(s(254f), t(860f))
        arcTo(Rect(s(224f), t(800f), s(284f), t(860f)), 90f, 90f, false)
        lineTo(s(432f), t(540f))
        lineTo(s(264f), t(540f))
        arcTo(Rect(s(184f), t(380f), s(344f), t(540f)), 90f, 180f, false)
        lineTo(s(432f), t(380f))
        close()
    }
    drawPath(body, Color.White, style = Fill)

    // Three tapering stripes
    listOf(
        listOf(488f to 570f, 536f to 570f, 542f to 640f, 482f to 640f),
        listOf(478f to 680f, 546f to 680f, 554f to 760f, 470f to 760f),
        listOf(464f to 800f, 560f to 800f, 570f to 860f, 454f to 860f)
    ).forEach { pts ->
        drawPath(Path().apply {
            moveTo(s(pts[0].first), t(pts[0].second))
            pts.drop(1).forEach { lineTo(s(it.first), t(it.second)) }
            close()
        }, Color.White, style = Fill)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Screen
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalInfoScreen(
    onSaveAndContinue: (
        bloodGroup: String,
        allergies: String,
        chronicConditions: String,
        reportUri: Uri?
    ) -> Unit = { _, _, _, _ -> }
) {
    var bloodGroup        by remember { mutableStateOf("") }
    var bloodGroupExpanded by remember { mutableStateOf(false) }
    var allergies          by remember { mutableStateOf("") }
    var chronicConditions  by remember { mutableStateOf("") }
    var reportUri          by remember { mutableStateOf<Uri?>(null) }
    var reportName         by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            reportUri  = it
            reportName = it.lastPathSegment ?: "Report uploaded"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.Start
        ) {

            // Language selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = SubtitleColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("English", color = SubtitleColor, fontSize = 13.sp)
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = SubtitleColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Logo
            AppLogo(
                size = 72.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            // Heading
            Text(
                text = "Medical\nInformation",
                color = TitleWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Fill in your health details below",
                color = SubtitleColor,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(24.dp))

            // ── 1. Blood Group Dropdown ───────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = bloodGroupExpanded,
                onExpandedChange = { bloodGroupExpanded = it }
            ) {
                OutlinedTextField(
                    value = bloodGroup,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    placeholder = {
                        Text("Blood Group", color = FieldPlaceholder, fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Favorite,
                            contentDescription = null,
                            tint = FieldPlaceholder,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodGroupExpanded)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = whiteFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = bloodGroupExpanded,
                    onDismissRequest = { bloodGroupExpanded = false }
                ) {
                    bloodGroups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group, fontSize = 14.sp) },
                            onClick = {
                                bloodGroup = group
                                bloodGroupExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 2. Allergies (optional) ───────────────────────────────────
            OutlinedTextField(
                value = allergies,
                onValueChange = { allergies = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Allergies (optional)", color = FieldPlaceholder, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = FieldPlaceholder,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Text(
                        "Optional",
                        color = OptionalGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = whiteFieldColors()
            )

            Spacer(Modifier.height(12.dp))

            // ── 3. Chronic Conditions ─────────────────────────────────────
            OutlinedTextField(
                value = chronicConditions,
                onValueChange = { chronicConditions = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                placeholder = {
                    Text("Chronic Conditions", color = FieldPlaceholder, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.MonitorHeart,
                        contentDescription = null,
                        tint = FieldPlaceholder,
                        modifier = Modifier.size(18.dp)
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = whiteFieldColors()
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
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = ReportLabelBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "CURRENT MEDICAL REPORT",
                        color = ReportLabelBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Optional",
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
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = null,
                            tint = ButtonBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (reportName.isEmpty()) "Tap to upload report" else reportName,
                            color = if (reportName.isEmpty()) Color(0xFF5A6A8A) else ButtonBlue,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (reportName.isEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "PDF, JPG, PNG supported",
                                color = OptionalGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Save & Continue Button ────────────────────────────────────
            Button(
                onClick = {
                    onSaveAndContinue(
                        bloodGroup,
                        allergies,
                        chronicConditions,
                        reportUri
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue)
            ) {
                Text(
                    text = "Save & Continue",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Color Helper — white field style matching the photo
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
private fun whiteFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor  = FieldWhite,
    focusedContainerColor    = FieldWhite,
    unfocusedBorderColor     = Color.Transparent,
    focusedBorderColor       = ButtonBlue,
    unfocusedTextColor       = FieldText,
    focusedTextColor         = FieldText,
    cursorColor              = ButtonBlue
)

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