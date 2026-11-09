package com.example.findme.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.findme.data.local.AppAccent

fun brandAccent(accent: AppAccent): Color = when (accent) {
    AppAccent.Indigo -> BrandLime
    AppAccent.Mint -> BrandCyan
    AppAccent.Rose -> BrandVioletLight
}

private val BrandColors = darkColorScheme(
    primary = BrandLime, onPrimary = BrandNight,
    primaryContainer = IndigoCardBg, onPrimaryContainer = TextPrimary,
    secondary = BrandCyan, onSecondary = BrandNight,
    secondaryContainer = Color(0xFF19334F), onSecondaryContainer = TextPrimary,
    tertiary = BrandVioletLight, onTertiary = BrandNight,
    tertiaryContainer = Color(0xFF302450), onTertiaryContainer = TextPrimary,
    background = DarkBackground, onBackground = TextPrimary,
    surface = DarkSurface, onSurface = TextPrimary,
    surfaceVariant = DarkInnerBg, onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkCardBg,
    surfaceContainerHigh = DarkInnerBg,
    surfaceContainerHighest = Color(0xFF293653),
    outline = DarkCardBorder, outlineVariant = DarkCardBorder,
    error = Color(0xFFFFA2AD), onError = BrandNight,
    errorContainer = Color(0xFF482333), onErrorContainer = Color(0xFFFFD9DE),
    inverseSurface = TextPrimary, inverseOnSurface = BrandNight, inversePrimary = Color(0xFF365C1C),
    surfaceTint = BrandCyan
)

@Composable
fun FindmeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    accent: AppAccent = AppAccent.Indigo,
    content: @Composable () -> Unit
) {
    // Persisted enum names remain compatible with the previous appearance settings.
    val primary by animateColorAsState(brandAccent(accent), tween(250), label = "themeAccent")
    val container by animateColorAsState(when (accent) {
        AppAccent.Indigo -> IndigoCardBg
        AppAccent.Mint -> Color(0xFF19334F)
        AppAccent.Rose -> Color(0xFF302450)
    }, tween(250), label = "themeContainer")
    MaterialTheme(
        colorScheme = BrandColors.copy(primary = primary, primaryContainer = container),
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
        content = content
    )
}
