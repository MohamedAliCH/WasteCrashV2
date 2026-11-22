package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WasteCrushColorScheme = darkColorScheme(
    primary = CeramicBlueTop,
    onPrimary = Color.White,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = TextOnDark,
    secondary = EcoEmerald,
    onSecondary = Color.White,
    secondaryContainer = EcoEmeraldDark,
    onSecondaryContainer = TextOnDark,
    tertiary = SaffronGold,
    onTertiary = DeepNavyInk,
    tertiaryContainer = SaffronGoldDark,
    onTertiaryContainer = Color.White,
    error = CarthageCoral,
    onError = Color.White,
    background = DarkBgPrimary,
    onBackground = TextOnDark,
    surface = DarkSurfaceCard,
    onSurface = TextOnDark,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextMutedOnDark,
    outline = DarkBorderSubtle
)

val WasteCrushShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun WasteCrushTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WasteCrushColorScheme,
        typography = WasteCrushTypography,
        shapes = WasteCrushShapes,
        content = content
    )
}
