package com.project.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val isDark = LocalIsDark.current
    
    var showPass by remember { mutableStateOf(false) }

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

                CustomTextField(
                    value = s.phone,
                    onValueChange = { s.phone = it },
                    placeholder = t.phoneNumber,
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isDark = isDark
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
                    city = s.city,           onCityChange = { s.city = it },
                    state = s.state,         onStateChange = { s.state = it },
                    country = s.country,     onCountryChange = { s.country = it },
                    exactArea = s.exactArea, onExactAreaChange = { s.exactArea = it },
                    isDark = isDark
                )

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
                            onCreateAccount(
                                s.username, s.fullName, s.phone, s.password,
                                s.city, s.state, s.country, s.exactArea
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

@Composable
fun AddressSection(
    city: String,      onCityChange: (String) -> Unit,
    state: String,     onStateChange: (String) -> Unit,
    country: String,   onCountryChange: (String) -> Unit,
    exactArea: String, onExactAreaChange: (String) -> Unit,
    isDark: Boolean
) {
    val t = Translations.get(LocalLanguage.current)
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
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
                CustomTextField(
                    value = city,
                    onValueChange = onCityChange,
                    placeholder = t.city,
                    isDark = isDark
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                CustomTextField(
                    value = state,
                    onValueChange = onStateChange,
                    placeholder = t.state,
                    isDark = isDark
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        CustomTextField(
            value = country,
            onValueChange = onCountryChange,
            placeholder = t.country,
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

@Preview(showBackground = true, widthDp = 360, heightDp = 820)
@Composable
fun CreateAccountScreenPreview() {
    CreateAccountScreen()
}
