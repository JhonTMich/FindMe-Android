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
    AppAccent.Indigo -> BrandAccent
    AppAccent.Mint -> BrandSky
    AppAccent.Rose -> BrandSlate
}

private val BrandColors = darkColorScheme(
    primary = BrandAccent, onPrimary = BrandNight,
    primaryContainer = AccentContainer, onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = BrandSky, onSecondary = Color(0xFF0E2B25),
    secondaryContainer = SkyContainer, onSecondaryContainer = Color(0xFFC8F2E6),
    tertiary = BrandSlate, onTertiary = Color(0xFF241A45),
    tertiaryContainer = SlateContainer, onTertiaryContainer = Color(0xFFE8DDFF),
    background = AppBackground, onBackground = TextPrimary,
    surface = AppSurface, onSurface = TextPrimary,
    surfaceVariant = InnerBg, onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color(0xFF1A1C36),
    surfaceContainerLow = Color(0xFF232747),
    surfaceContainer = AppSurface,
    surfaceContainerHigh = CardBg,
    surfaceContainerHighest = InnerBg,
    outline = Color(0xFF6E78A6), outlineVariant = CardBorder,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = BrandCream, inverseOnSurface = BrandNight, inversePrimary = BrandIconBlue,
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
