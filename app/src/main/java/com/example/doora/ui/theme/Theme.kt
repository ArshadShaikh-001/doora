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

private val DarkColorScheme = darkColorScheme(
    primary = DooraPrimaryDark,
    onPrimary = DooraOnPrimaryDark,
    primaryContainer = DooraPrimaryContainerDark,
    onPrimaryContainer = DooraOnPrimaryContainerDark,
    secondary = DooraSecondaryDark,
    onSecondary = DooraOnSecondaryDark,
    secondaryContainer = DooraSecondaryContainerDark,
    onSecondaryContainer = DooraOnSecondaryContainerDark,
    background = DooraBackgroundDark,
    onBackground = DooraOnBackgroundDark,
    surface = DooraSurfaceDark,
    onSurface = DooraOnSurfaceDark,
    surfaceVariant = DooraSurfaceVariantDark,
    onSurfaceVariant = DooraOnSurfaceVariantDark,
    outline = DooraOutlineDark,
    outlineVariant = DooraOutlineVariantDark
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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