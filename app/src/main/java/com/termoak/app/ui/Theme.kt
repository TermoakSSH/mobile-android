package com.termoak.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.data.Prefs
import com.termoak.app.data.ThemeMode

// The desktop colors (src/theme.rs of TermoakSSH/desktop).
object Brand {
    val Blue = Color(0xFF4F7CFF)
    val BlueLight = Color(0xFF2F6FEB)
    val Green = Color(0xFF3FB27F)
    val Amber = Color(0xFFE8A33D)
    val Red = Color(0xFFE5534B)
    val TerminalBg = Color(0xFF12151D)
}

private val Dark = darkColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1F2F5C),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = Color(0xFF8FA3C8),
    secondaryContainer = Color(0xFF263045),
    onSecondaryContainer = Color(0xFFD6DBE4),
    tertiary = Brand.Green,
    // Deep navy blue, like Termius' dark theme.
    background = Color(0xFF0F121A),
    onBackground = Color(0xFFE6E9F0),
    surface = Color(0xFF0F121A),
    onSurface = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFF1C2130),
    onSurfaceVariant = Color(0xFF8E97AB),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF161A24),
    surfaceContainer = Color(0xFF1A1F2B),
    surfaceContainerHigh = Color(0xFF212736),
    surfaceContainerHighest = Color(0xFF283042),
    outline = Color(0xFF363F54),
    outlineVariant = Color(0xFF232A3A),
    error = Brand.Red,
)

private val Light = lightColorScheme(
    primary = Brand.BlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B2A6B),
    secondary = Color(0xFF55627A),
    secondaryContainer = Color(0xFFE3E8F2),
    onSecondaryContainer = Color(0xFF1F2328),
    tertiary = Color(0xFF1A7F37),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFF7F8FB),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE9ECF3),
    onSurfaceVariant = Color(0xFF5A6478),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F8),
    surfaceContainer = Color(0xFFECEFF5),
    surfaceContainerHigh = Color(0xFFE6E9F1),
    surfaceContainerHighest = Color(0xFFE0E4ED),
    outline = Color(0xFFC3CAD8),
    outlineVariant = Color(0xFFDDE2EC),
    error = Color(0xFFCF222E),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun TermoakTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val mode by prefs.theme.collectAsState()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
