package com.example.policeapp.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PoliceColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = GradientBlueStart,
    onPrimaryContainer = Color.White,
    secondary = AccentGold,
    onSecondary = Color.Black,
    secondaryContainer = NavyLight,
    onSecondaryContainer = TextPrimary,
    tertiary = BrightBlue,
    onTertiary = Color.White,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = AccentRed,
    onError = Color.White,
)

@Composable
fun PoliceAppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PoliceColorScheme,
        typography = Typography,
        content = content,
    )
}
