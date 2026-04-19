package com.project.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Colors — Exact match from Register Screen
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
private val BackgroundTop     = Color(0xFF283B5A)
private val BackgroundBottom  = Color(0xFF1E212B)
private val PrimaryBlue       = Color(0xFF336CFC)
private val TextPrimary       = Color.White
private val TextSecondary     = Color.LightGray
private val TextMuted         = Color.Gray

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Language Logic
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Removed local languages and LanguageSwitcher (migrated to Translations.kt and App.kt)

// Using getSwiftAidIcon() from SignInScreen instead of drawing locally

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Screen
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun CreateAccountScreen(
    onLanguageChange: (String) -> Unit = {},
    onCreateAccount: (
        username: String,
        fullName: String,
        phone: String,
        password: String,
        city: String,
        state: String,
        country: String,
        exactArea: String
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onBack: () -> Unit = {}
) {
    val t = Translations.get(LocalLanguage.current)
    val s = LocalSharedState.current
    
    
    var showPass  by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundTop, BackgroundBottom)
                )
            )
    ) {
        // Removed blue glow bloom to match SignInScreen evenly

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

            Text(
                text = t.createAccountTitle,
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = t.createAccountSubtitle,
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            FormField(
                value = s.username,
                onValueChange = { s.username = it },
                placeholder = t.username,
                leadingIcon = Icons.Default.Person
            )
            Spacer(Modifier.height(12.dp))

            FormField(
                value = s.fullName,
                onValueChange = { s.fullName = it },
                placeholder = t.fullName,
                leadingIcon = Icons.Default.AccountCircle
            )
            Spacer(Modifier.height(12.dp))

            FormField(
                value = s.phone,
                onValueChange = { s.phone = it },
                placeholder = t.phoneNumber,
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
            )
            Spacer(Modifier.height(12.dp))

            PasswordField(
                value = s.password,
                onValueChange = { s.password = it },
                placeholder = t.password,
                showPassword = showPass,
                onToggleVisibility = { showPass = !showPass }
            )
            Spacer(Modifier.height(12.dp))

            AddressSection(
                city = s.city,           onCityChange = { s.city = it },
                state = s.state,         onStateChange = { s.state = it },
                country = s.country,     onCountryChange = { s.country = it },
                exactArea = s.exactArea, onExactAreaChange = { s.exactArea = it }
            )

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
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Text(
                        text = t.backBtn,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        onCreateAccount(
                            s.username, s.fullName, s.phone, s.password,
                            s.city, s.state, s.country, s.exactArea
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
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
// Reusable Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.White, RoundedCornerShape(12.dp)),
        textStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 16.sp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(leadingIcon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color.Gray, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.White, RoundedCornerShape(12.dp)),
        textStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 16.sp),
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color.Gray, fontSize = 16.sp)
                    }
                    innerTextField()
                }
                Icon(
                    imageVector = getEyeIcon(showPassword),
                    contentDescription = null,
                    modifier = Modifier.clickable(onClick = onToggleVisibility).size(20.dp),
                    tint = Color.Gray
                )
            }
        }
    )
}

@Composable
fun AddressSection(
    city: String,      onCityChange: (String) -> Unit,
    state: String,     onStateChange: (String) -> Unit,
    country: String,   onCountryChange: (String) -> Unit,
    exactArea: String, onExactAreaChange: (String) -> Unit
) {
    val t = Translations.get(LocalLanguage.current)
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF336CFC), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(t.addressTitle.uppercase(), color = Color(0xFF336CFC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SubField(value = city,  onValueChange = onCityChange,  placeholder = t.city,  modifier = Modifier.weight(1f))
            SubField(value = state, onValueChange = onStateChange, placeholder = t.state, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        SubField(value = country, onValueChange = onCountryChange, placeholder = t.country, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(12.dp))

        BasicTextField(
            value = exactArea,
            onValueChange = onExactAreaChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp)
                .background(Color.White, RoundedCornerShape(12.dp)),
            textStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 16.sp),
            maxLines = 4,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (exactArea.isEmpty()) {
                        Text(t.exactLocation, color = Color.Gray, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun SubField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(52.dp)
            .background(Color.White, RoundedCornerShape(12.dp)),
        textStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 16.sp),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Color.Gray, fontSize = 16.sp)
                }
                innerTextField()
            }
        }
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Color Helpers
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.Transparent,
    focusedContainerColor   = Color.Transparent,
    unfocusedBorderColor    = Color.Transparent,
    focusedBorderColor      = Color.Transparent,
    cursorColor             = PrimaryBlue
)

@Composable
fun subFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.Transparent,
    focusedContainerColor   = Color.Transparent,
    unfocusedBorderColor    = Color.Transparent,
    focusedBorderColor      = Color.Transparent,
    cursorColor             = PrimaryBlue
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Preview
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Preview(showBackground = true, backgroundColor = 0xFF131829, widthDp = 360, heightDp = 820)
@Composable
fun CreateAccountScreenPreview() {
    CreateAccountScreen()
}