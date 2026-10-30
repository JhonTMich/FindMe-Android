package com.example.findme.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import com.example.findme.data.local.AppAccent

private val DarkColorScheme = darkColorScheme(
    primary = AccentWork,
    secondary = AccentFree,
    tertiary = AccentUniversity,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCardBg,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder
)

@Composable
fun FindmeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    accent: AppAccent = AppAccent.Indigo,
    content: @Composable () -> Unit
) {
    val colors = when (accent) {
        AppAccent.Indigo -> Triple(IndigoPrimary, IndigoCardBg, IndigoCardBorder)
        AppAccent.Mint -> Triple(Color(0xFF69DAB3), Color(0xFF102F2C), Color(0xFF28866A))
        AppAccent.Rose -> Triple(Color(0xFFF5A3C6), Color(0xFF321D30), Color(0xFF995176))
    }
    val primary by animateColorAsState(colors.first, tween(250), label = "themeAccent")
    val container by animateColorAsState(colors.second, tween(250), label = "themeContainer")
    val border by animateColorAsState(colors.third, tween(250), label = "themeBorder")
    MaterialTheme(
        colorScheme = DarkColorScheme.copy(primary = primary, primaryContainer = container,
            onPrimaryContainer = TextPrimary, outlineVariant = border),
        typography = Typography,
        content = content
    )
}
