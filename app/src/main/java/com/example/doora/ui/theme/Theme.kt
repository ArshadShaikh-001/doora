package com.example.doora.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF14120E),
    primaryContainer = Color(0xFF332A1C),
    onPrimaryContainer = GoldPrimary,
    secondary = GoldSecondary,
    onSecondary = Color(0xFF14120E),
    secondaryContainer = GoldPillBg,
    onSecondaryContainer = GoldPrimary,
    background = LuxuryBackground,
    onBackground = TextPrimaryDark,
    surface = LuxuryCardSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = LuxuryCompletedCardSurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = GoldSecondary.copy(alpha = 0.4f),
    outlineVariant = GoldBorder
)

private val LightColorScheme = lightColorScheme(
    primary = DooraPrimaryLight,
    onPrimary = DooraOnPrimaryLight,
    primaryContainer = DooraPrimaryContainerLight,
    onPrimaryContainer = DooraOnPrimaryContainerLight,
    secondary = DooraSecondaryLight,
    onSecondary = DooraOnSecondaryLight,
    secondaryContainer = DooraSecondaryContainerLight,
    onSecondaryContainer = DooraOnSecondaryContainerLight,
    background = DooraBackgroundLight,
    onBackground = DooraOnBackgroundLight,
    surface = DooraSurfaceLight,
    onSurface = DooraOnSurfaceLight,
    surfaceVariant = DooraSurfaceVariantLight,
    onSurfaceVariant = DooraOnSurfaceVariantLight,
    outline = DooraOutlineLight,
    outlineVariant = DooraOutlineVariantLight
)

val DooraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun DooraTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DooraShapes,
        content = content
    )
}