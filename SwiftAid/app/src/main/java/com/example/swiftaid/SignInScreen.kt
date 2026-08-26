package com.example.swiftaid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun SignInScreen(
    onSignInClick: (String, String) -> Unit = { _, _ -> },
    onSignUpClick: () -> Unit = {},
    onGoogleSignInClick: () -> Unit = {}
) {
    val t = Translations.get(LocalLanguage.current)
    val isDark = LocalIsDark.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }

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
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBarToggles()

            Spacer(modifier = Modifier.height(16.dp))

            // Shield Logo
            Icon(
                imageVector = getSwiftAidIcon(),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Texts
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = t.signInTitle,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black,
                        lineHeight = 40.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = t.signInSubtitle,
                        fontSize = 14.sp,
                        color = if (isDark) Color.LightGray else Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Email Input
                LoginTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = t.email,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isDark = isDark
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password Input
                LoginTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = t.password,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onPasswordToggle = { passwordVisible = !passwordVisible },
                    isDark = isDark
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Remember me + Forgot Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            modifier = Modifier.offset(x = (-8).dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF4A7FF5),
                                uncheckedColor = if (isDark) Color.LightGray else Color.Gray,
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = t.rememberMe,
                            modifier = Modifier.offset(x = (-8).dp),
                            color = if (isDark) Color.LightGray else Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                    
                    Text(
                        text = t.forgotPassword,
                        color = Color(0xFF4A7FF5),
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.clickable { }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Log In Button
                Button(
                    onClick = { onSignInClick(email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                        contentColor = if (isDark) Color.Black else Color.White
                    )
                ) {
                    Text(
                        text = t.loginBtn,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Or divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDark) Color.DarkGray else Color.LightGray,
                        thickness = 1.dp
                    )
                    Text(
                        text = "Or",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = if (isDark) Color.Gray else Color.DarkGray,
                        fontSize = 14.sp
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDark) Color.DarkGray else Color.LightGray,
                        thickness = 1.dp
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Social Buttons
                OutlinedButton(
                    onClick = onGoogleSignInClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
                        contentColor = if (isDark) Color.White else Color.Black
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                    )
                ) {
                    Icon(
                        imageVector = getGoogleIconDetailed(),
                        contentDescription = "Google",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = t.continueWithGoogle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                

                Spacer(modifier = Modifier.height(48.dp))
                
                // Sign up
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t.noAccount + " ",
                        color = if (isDark) Color.Gray else Color.DarkGray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = t.signUpText,
                        color = Color(0xFF4A7FF5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onSignUpClick() }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SignInScreenPreview() {
    MaterialTheme {
        SignInScreen()
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isDark: Boolean,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        placeholder = {
            Text(
                text = placeholder,
                color = if (isDark) Color.Gray else Color.DarkGray
            )
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                Icon(
                    imageVector = getEyeIcon(passwordVisible),
                    contentDescription = "Toggle Password Visibility",
                    modifier = Modifier.clickable { onPasswordToggle() },
                    tint = if (isDark) Color.Gray else Color.DarkGray
                )
            }
        } else {
            null
        },
        textStyle = LocalTextStyle.current.copy(
            color = if (isDark) Color.White else Color.Black,
            fontSize = 16.sp
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = if (isDark) Color.White else Color.Black,
            unfocusedTextColor = if (isDark) Color.White else Color.Black,
            cursorColor = if (isDark) Color.White else Color.Black,
            focusedBorderColor = Color(0xFF4A7FF5),
            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
            focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
            unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
        )
    )
}
