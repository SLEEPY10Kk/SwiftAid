package com.example.user

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
private val BackgroundTop     = Color(0xFF181D31)
private val BackgroundBottom  = Color(0xFF131829)
private val PrimaryBlue       = Color(0xFF3772FF)
private val TextPrimary       = Color(0xFFFFFFFF)
private val TextSecondary     = Color(0xFFB0B3C6)
private val TextMuted         = Color(0xFF6B7280)
private val CardDark          = Color(0xFF131829)
private val CardBorder        = Color(0xFFFFFFFF).copy(alpha = 0.10f)

private val InputTextColor    = Color(0xFF1E293B)
private val LogoGradientStart = Color(0xFF3772FF)
private val LogoGradientEnd   = Color(0xFF0027A8)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Language Logic
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
data class Language(val name: String, val code: String)

val SupportedLanguages = listOf(
    Language("English", "en"),
    Language("हिन्दी", "hi"),
    Language("ગુજરાતી", "gu"),
    Language("தமிழ்", "ta"),
    Language("తెలుగు", "te"),
    Language("मराठी", "mr"),
    Language("বাংলা", "bn")
)

@Composable
fun LanguageSwitcher(onLanguageChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentLocaleCode = context.resources.configuration.locales[0].language
    val currentLanguage = SupportedLanguages.find { it.code == currentLocaleCode } ?: SupportedLanguages[0]

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.TopEnd) {
        TextButton(onClick = { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSecondary)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currentLanguage.name,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = TextSecondary)
            }
        }
        DropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BackgroundTop).border(1.dp, CardBorder)
        ) {
            SupportedLanguages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.name, color = if (lang.code == currentLocaleCode) PrimaryBlue else TextPrimary) },
                    onClick = {
                        onLanguageChange(lang.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
            var localeCode by remember { 
                mutableStateOf(prefs.getString("lang", Locale.getDefault().language) ?: "en") 
            }
            
            // Create a localized context that forces the UI to use the specific language
            val localizedContext = remember(localeCode) {
                val locale = Locale(localeCode)
                Locale.setDefault(locale)
                val config = android.content.res.Configuration(context.resources.configuration)
                config.setLocale(locale)
                context.createConfigurationContext(config)
            }

            // Wrapping with CompositionLocalProvider ensures the ENTIRE page respects the new context
            CompositionLocalProvider(LocalContext provides localizedContext) {
                CreateAccountScreen(onLanguageChange = { newLang ->
                    localeCode = newLang
                    prefs.edit().putString("lang", newLang).apply()
                })
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Logo — drawn entirely with Compose Canvas (no XML file needed)
// Matches the exact SVG: blue gradient rounded-square + white icon paths
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind { drawLogo() }
    )
}

/**
 * Draws the logo inside a DrawScope.
 */
fun DrawScope.drawLogo() {
    val vbSize = 1024f
    val scaleX = size.width / vbSize
    val scaleY = size.height / vbSize

    fun s(x: Float) = x * scaleX
    fun t(y: Float) = y * scaleY

    // ── Background: squircle path from SVG ──────────────────────
    val bgPath = Path().apply {
        moveTo(s(512f), t(0f))
        cubicTo(s(745.5f), t(0f), s(873.3f), t(0f), s(947.6f), t(74.4f))
        cubicTo(s(1022f), t(148.7f), s(1022f), t(276.5f), s(1022f), t(512f))
        cubicTo(s(1022f), t(747.5f), s(1022f), t(875.3f), s(947.6f), t(949.6f))
        cubicTo(s(873.3f), t(1024f), s(745.5f), t(1024f), s(512f), t(1024f))
        cubicTo(s(278.5f), t(1024f), s(150.7f), t(1024f), s(76.4f), t(949.6f))
        cubicTo(s(2f), t(875.3f), s(2f), t(747.5f), s(2f), t(512f))
        cubicTo(s(2f), t(276.5f), s(2f), t(148.7f), s(76.4f), t(74.4f))
        cubicTo(s(150.7f), t(0f), s(278.5f), t(0f), s(512f), t(0f))
        close()
    }

    val bgBrush = Brush.linearGradient(
        colors = listOf(LogoGradientStart, LogoGradientEnd),
        start = Offset(s(vbSize * 0.10f), t(0f)),
        end = Offset(s(vbSize * 0.90f), t(vbSize))
    )
    drawPath(bgPath, brush = bgBrush)

    // ── Icon: Main tower with cut-out slots (EvenOdd) ───────────────────
    val iconBrush = Brush.linearGradient(
        colors = listOf(Color.White, Color(0xFFF2F2F7)),
        start = Offset(s(512f), t(170f)),
        end = Offset(s(512f), t(860f))
    )

    val iconPath = Path().apply {
        fillType = PathFillType.EvenOdd

        // Main Shape
        moveTo(s(432f), t(170f))
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(s(432f), t(90f), s(592f), t(250f)),
            startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false
        )
        lineTo(s(592f), t(380f))
        lineTo(s(760f), t(380f))
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(s(680f), t(380f), s(840f), t(540f)),
            startAngleDegrees = 270f, sweepAngleDegrees = 180f, forceMoveTo = false
        )
        lineTo(s(592f), t(540f))
        lineTo(s(800f), t(830f))
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(s(740f), t(800f), s(800f), t(860f)),
            startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false
        )
        lineTo(s(254f), t(860f))
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(s(224f), t(800f), s(284f), t(860f)),
            startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false
        )
        lineTo(s(432f), t(540f))
        lineTo(s(264f), t(540f))
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(s(184f), t(380f), s(344f), t(540f)),
            startAngleDegrees = 90f, sweepAngleDegrees = 180f, forceMoveTo = false
        )
        lineTo(s(432f), t(380f))
        close()

        // Slot 1 (Hole)
        moveTo(s(488f), t(570f))
        lineTo(s(536f), t(570f))
        lineTo(s(542f), t(640f))
        lineTo(s(482f), t(640f))
        close()

        // Slot 2 (Hole)
        moveTo(s(478f), t(680f))
        lineTo(s(546f), t(680f))
        lineTo(s(554f), t(760f))
        lineTo(s(470f), t(760f))
        close()

        // Slot 3 (Hole)
        moveTo(s(464f), t(800f))
        lineTo(s(560f), t(800f))
        lineTo(s(570f), t(860f))
        lineTo(s(454f), t(860f))
        close()
    }

    drawPath(iconPath, brush = iconBrush)
}

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
    ) -> Unit = { _, _, _, _, _, _, _, _ -> }
) {
    var username  by remember { mutableStateOf("") }
    var fullName  by remember { mutableStateOf("") }
    var phone     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }
    var city      by remember { mutableStateOf("") }
    var state     by remember { mutableStateOf("") }
    var country   by remember { mutableStateOf("") }
    var exactArea by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundTop, BackgroundBottom)
                )
            )
    ) {
        /* Top Blue Glow Bloom from Register Page */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(0.5f, 0f),
                        radius = 1200f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.Start
        ) {
            LanguageSwitcher(onLanguageChange = onLanguageChange)

            // Logo
            AppLogo(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = 72.dp
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.create_account_title),
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.subtitle),
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            FormField(
                value = username,
                onValueChange = { username = it },
                placeholder = stringResource(R.string.username),
                leadingIcon = Icons.Outlined.Person
            )
            Spacer(Modifier.height(12.dp))

            FormField(
                value = fullName,
                onValueChange = { fullName = it },
                placeholder = stringResource(R.string.full_name),
                leadingIcon = Icons.Outlined.AccountCircle
            )
            Spacer(Modifier.height(12.dp))

            FormField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = stringResource(R.string.phone_number),
                leadingIcon = Icons.Outlined.Phone,
                keyboardType = KeyboardType.Phone
            )
            Spacer(Modifier.height(12.dp))

            PasswordField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.password),
                showPassword = showPass,
                onToggleVisibility = { showPass = !showPass }
            )
            Spacer(Modifier.height(12.dp))

            AddressSection(
                city              = city,
                state             = state,
                country           = country,
                exactArea         = exactArea,
                onCityChange      = { city = it },
                onStateChange     = { state = it },
                onCountryChange   = { country = it },
                onExactAreaChange = { exactArea = it }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    onCreateAccount(
                        username, fullName, phone, password,
                        city, state, country, exactArea
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    text = stringResource(R.string.create_account_btn),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
            leadingIcon = {
                Icon(leadingIcon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(16.dp),
            colors = outlinedFieldColors()
        )
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp),
            colors = outlinedFieldColors()
        )
    }
}

@Composable
fun AddressSection(
    city: String,      onCityChange: (String) -> Unit,
    state: String,     onStateChange: (String) -> Unit,
    country: String,   onCountryChange: (String) -> Unit,
    exactArea: String, onExactAreaChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.address_label), color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SubField(value = city,  onValueChange = onCityChange,  placeholder = stringResource(R.string.city),  modifier = Modifier.weight(1f))
            SubField(value = state, onValueChange = onStateChange, placeholder = stringResource(R.string.state), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(9.dp))

        SubField(value = country, onValueChange = onCountryChange, placeholder = stringResource(R.string.country), modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(9.dp))

        OutlinedTextField(
            value = exactArea,
            onValueChange = onExactAreaChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            placeholder = { Text(stringResource(R.string.address_hint), color = TextMuted, fontSize = 13.sp) },
            maxLines = 4,
            shape = RoundedCornerShape(10.dp),
            colors = subFieldColors()
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = subFieldColors()
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Color Helpers
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.White,
    focusedContainerColor   = Color.White,
    unfocusedBorderColor    = Color.Transparent,
    focusedBorderColor      = Color.Transparent,
    unfocusedTextColor      = InputTextColor,
    focusedTextColor        = InputTextColor,
    cursorColor             = PrimaryBlue
)

@Composable
fun subFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color(0xFFF1F5F9),
    focusedContainerColor   = Color(0xFFF1F5F9),
    unfocusedBorderColor    = Color.Transparent,
    focusedBorderColor      = Color.Transparent,
    unfocusedTextColor      = InputTextColor,
    focusedTextColor        = InputTextColor,
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