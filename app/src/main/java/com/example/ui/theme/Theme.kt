package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = JarvisVoid,
    primaryContainer = DeepCyanDim,
    onPrimaryContainer = IceWhite,
    secondary = CyberOrange,
    onSecondary = JarvisVoid,
    secondaryContainer = JarvisUserBubble,
    onSecondaryContainer = IceWhite,
    tertiary = NeonSapphire,
    onTertiary = IceWhite,
    background = JarvisVoid,
    onBackground = IceWhite,
    surface = JarvisSurface,
    onSurface = IceWhite,
    surfaceVariant = JarvisSurfaceElevated,
    onSurfaceVariant = SlateCyan,
    outline = ElectricCyan.copy(alpha = 0.45f),
    outlineVariant = CyberOrange.copy(alpha = 0.35f),
    error = HudAlertRed,
    onError = IceWhite,
    errorContainer = HudAlertContainer,
    onErrorContainer = IceWhite
)

private val JarvisShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PanduBabuTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
