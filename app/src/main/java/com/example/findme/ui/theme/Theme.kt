package com.example.findme.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.findme.data.local.AppAccent

fun brandAccent(accent: AppAccent): Color = when (accent) {
    AppAccent.Indigo -> BrandAccent
    AppAccent.Mint -> BrandSky
    AppAccent.Rose -> BrandSlate
}

private val BrandColors = lightColorScheme(
    primary = BrandAccent, onPrimary = Color.White,
    primaryContainer = AccentContainer, onPrimaryContainer = Color(0xFF4A1505),
    secondary = BrandSky, onSecondary = Color.White,
    secondaryContainer = SkyContainer, onSecondaryContainer = Color(0xFF05332E),
    tertiary = BrandSlate, onTertiary = Color.White,
    tertiaryContainer = SlateContainer, onTertiaryContainer = Color(0xFF1C1E5C),
    background = AppBackground, onBackground = TextPrimary,
    surface = AppSurface, onSurface = TextPrimary,
    surfaceVariant = InnerBg, onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF6),
    surfaceContainer = Color(0xFFFFF4EA),
    surfaceContainerHigh = InnerBg,
    surfaceContainerHighest = Color(0xFFF6E2D2),
    outline = Color(0xFFCDBDAB), outlineVariant = CardBorder,
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    inverseSurface = BrandInk, inverseOnSurface = BrandCream, inversePrimary = Color(0xFFFFB59E),
    surfaceTint = BrandAccent
)

@Composable
fun NearTheme(
    accent: AppAccent = AppAccent.Indigo,
    content: @Composable () -> Unit
) {
    // Persisted enum names remain compatible with the previous appearance settings.
    val primary by animateColorAsState(brandAccent(accent), tween(250), label = "themeAccent")
    val container by animateColorAsState(when (accent) {
        AppAccent.Indigo -> AccentContainer
        AppAccent.Mint -> SkyContainer
        AppAccent.Rose -> SlateContainer
    }, tween(250), label = "themeContainer")
    MaterialTheme(
        colorScheme = BrandColors.copy(primary = primary, primaryContainer = container),
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)),
        content = content
    )
}
