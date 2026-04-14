package com.project.swiftaid

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SignInScreen() {
    var email by remember { mutableStateOf("Loisbecket@gmail.com") }
    var password by remember { mutableStateOf("1234567") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF283B5A),
                        Color(0xFF1E212B),
                        Color(0xFF1E212B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Shield Logo
            Icon(
                imageVector = getSwiftAidIcon(),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(56.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Texts
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Sign in to your\nAccount",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 40.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Enter your email and password to log in",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Email Input
            BasicTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
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
                        if (email.isEmpty()) {
                            Text("Email", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password Input
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
                textStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 16.sp),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (password.isEmpty()) {
                                Text("Password", color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                        Icon(
                            imageVector = getEyeIcon(passwordVisible),
                            contentDescription = "Toggle Password Visibility",
                            modifier = Modifier.clickable { passwordVisible = !passwordVisible },
                            tint = Color.Gray
                        )
                    }
                }
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
                            uncheckedColor = Color.LightGray,
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = "Remember me",
                        modifier = Modifier.offset(x = (-8).dp),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
                
                Text(
                    text = "Forgot Password?",
                    color = Color(0xFF4A7FF5),
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.clickable { }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Log In Button
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF336CFC))
            ) {
                Text(
                    text = "Log In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
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
                    color = Color.DarkGray,
                    thickness = 1.dp
                )
                Text(
                    text = "Or",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.DarkGray,
                    thickness = 1.dp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Social Buttons
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Icon(
                    imageVector = getGoogleIcon(),
                    contentDescription = "Google",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Continue with Google",
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            

            Spacer(modifier = Modifier.height(24.dp))
            
            // Sign up
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account? ",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "Sign Up",
                    color = Color(0xFF4A7FF5),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Icons
fun getSwiftAidIcon(): ImageVector {
    return ImageVector.Builder(
        name = "SwiftAidLogo",
        defaultWidth = 64.dp,
        defaultHeight = 64.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ).apply {
        // Red background squircle shape from SVG
        path(fill = Brush.linearGradient(
            colors = listOf(Color(0xFF007AFF), Color(0xFF0027A8)),
            start = androidx.compose.ui.geometry.Offset(102f, 0f),
            end = androidx.compose.ui.geometry.Offset(921f, 1024f)
        )) {
            moveTo(512f, 0f)
            curveTo(745.5f, 0f, 873.3f, 0f, 947.6f, 74.4f)
            curveTo(1022f, 148.7f, 1022f, 276.5f, 1022f, 512f)
            curveTo(1022f, 747.5f, 1022f, 875.3f, 947.6f, 949.6f)
            curveTo(873.3f, 1024f, 745.5f, 1024f, 512f, 1024f)
            curveTo(278.5f, 1024f, 150.7f, 1024f, 76.4f, 949.6f)
            curveTo(2f, 875.3f, 2f, 747.5f, 2f, 512f)
            curveTo(2f, 276.5f, 2f, 148.7f, 76.4f, 74.4f)
            curveTo(150.7f, 0f, 278.5f, 0f, 512f, 0f)
            close()
        }

        // White logo graphic with parsed curves from SVG
        path(fill = Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F7)),
            start = androidx.compose.ui.geometry.Offset(512f, 0f),
            end = androidx.compose.ui.geometry.Offset(512f, 1024f)
        ), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
            moveTo(432f, 170f)
            arcTo(80f, 80f, 0f, false, true, 592f, 170f)
            lineTo(592f, 380f)
            lineTo(760f, 380f)
            arcTo(80f, 80f, 0f, false, true, 760f, 540f)
            lineTo(592f, 540f)
            lineTo(800f, 830f)
            arcTo(30f, 30f, 0f, false, true, 770f, 860f)
            lineTo(254f, 860f)
            arcTo(30f, 30f, 0f, false, true, 224f, 830f)
            lineTo(432f, 540f)
            lineTo(264f, 540f)
            arcTo(80f, 80f, 0f, false, true, 264f, 380f)
            lineTo(432f, 380f)
            close()

            moveTo(488f, 570f)
            lineTo(536f, 570f)
            lineTo(542f, 640f)
            lineTo(482f, 640f)
            close()

            moveTo(478f, 680f)
            lineTo(546f, 680f)
            lineTo(554f, 760f)
            lineTo(470f, 760f)
            close()

            moveTo(464f, 800f)
            lineTo(560f, 800f)
            lineTo(570f, 860f)
            lineTo(454f, 860f)
            close()
        }
    }.build()
}

fun getGoogleIcon(): ImageVector {
    return ImageVector.Builder(
        name = "Google",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFFEA4335))) {
            moveTo(11.8f, 5.2f)
            curveTo(13.5f, 5.2f, 15f, 5.8f, 16.2f, 6.9f)
            lineTo(19.2f, 3.9f)
            curveTo(17.3f, 2.1f, 14.8f, 1.1f, 11.8f, 1.1f)
            curveTo(7.1f, 1.1f, 3f, 3.8f, 1.1f, 7.8f)
            lineTo(4.4f, 10.4f)
            curveTo(5.2f, 7.4f, 8.3f, 5.2f, 11.8f, 5.2f)
        }
        path(fill = SolidColor(Color(0xFF4285F4))) {
            moveTo(23.5f, 12.2f)
            curveTo(23.5f, 11.3f, 23.4f, 10.5f, 23.3f, 9.7f)
            lineTo(11.8f, 9.7f)
            lineTo(11.8f, 14.4f)
            lineTo(18.5f, 14.4f)
            curveTo(18.2f, 16.3f, 17.1f, 18f, 15.6f, 19f)
            lineTo(19.3f, 21.9f)
            curveTo(21.6f, 19.8f, 23.5f, 16.3f, 23.5f, 12.2f)
        }
        path(fill = SolidColor(Color(0xFFFBBC05))) {
            moveTo(4.4f, 14.2f)
            curveTo(4.2f, 13.4f, 4.1f, 12.6f, 4.1f, 11.8f)
            curveTo(4.1f, 11f, 4.2f, 10.2f, 4.4f, 9.4f)
            lineTo(1.1f, 6.8f)
            curveTo(0.4f, 8.3f, 0f, 10f, 0f, 11.8f)
            curveTo(0f, 13.6f, 0.4f, 15.3f, 1.1f, 16.8f)
            lineTo(4.4f, 14.2f)
        }
        path(fill = SolidColor(Color(0xFF34A853))) {
            moveTo(11.8f, 22.5f)
            curveTo(14.8f, 22.5f, 17.5f, 21.5f, 19.4f, 19.7f)
            lineTo(15.7f, 16.9f)
            curveTo(14.6f, 17.6f, 13.3f, 18.1f, 11.8f, 18.1f)
            curveTo(8.2f, 18.1f, 5.2f, 15.8f, 4.4f, 12.9f)
            lineTo(1f, 15.5f)
            curveTo(3f, 19.8f, 7.1f, 22.5f, 11.8f, 22.5f)
        }
    }.build()
}


fun getEyeIcon(isVisible: Boolean): ImageVector {
    return ImageVector.Builder(
        name = "Eye",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        if (isVisible) {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f
            ) {
                moveTo(2f, 12f)
                curveTo(2f, 12f, 5f, 5f, 12f, 5f)
                curveTo(19f, 5f, 22f, 12f, 22f, 12f)
                curveTo(22f, 12f, 19f, 19f, 12f, 19f)
                curveTo(5f, 19f, 2f, 12f, 2f, 12f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f
            ) {
                moveTo(12f, 15f)
                curveTo(13.6569f, 15f, 15f, 13.6569f, 15f, 12f)
                curveTo(15f, 10.3431f, 13.6569f, 9f, 12f, 9f)
                curveTo(10.3431f, 9f, 9f, 10.3431f, 9f, 12f)
                curveTo(9f, 13.6569f, 10.3431f, 15f, 12f, 15f)
                close()
            }
        } else {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(2f, 12f)
                curveTo(2f, 12f, 5f, 5f, 12f, 5f)
                curveTo(19f, 5f, 22f, 12f, 22f, 12f)
                curveTo(22f, 12f, 19f, 19f, 12f, 19f)
                curveTo(5f, 19f, 2f, 12f, 2f, 12f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f
            ) {
                moveTo(12f, 15f)
                curveTo(13.6569f, 15f, 15f, 13.6569f, 15f, 12f)
                curveTo(15f, 10.3431f, 13.6569f, 9f, 12f, 9f)
                curveTo(10.3431f, 9f, 9f, 10.3431f, 9f, 12f)
                curveTo(9f, 13.6569f, 10.3431f, 15f, 12f, 15f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Gray),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
            }
        }
    }.build()
}

@Preview
@Composable
fun SignInScreenPreview() {
    MaterialTheme {
        SignInScreen()
    }
}
