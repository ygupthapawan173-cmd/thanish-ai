package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.OmegaVisualTheme
import com.example.data.OrbColorPreset

object OmegaColors {
    val PureBlack = Color(0xFF000000)
    val CarbonSurface = Color(0xFF101014)
    val CarbonElevated = Color(0xFF17171D)
    val SubtleBorder = Color(0xFF22222B)
    val HairlineDivider = Color(0xFF1C1C24)

    val VelvetTop = Color(0xFF0D0D12)
    val VelvetMid = Color(0xFF08080B)
    val VelvetBottom = Color(0xFF000000)

    val ElectricCyan = Color(0xFF38BDF8)
    val RoyalIndigo = Color(0xFF7C3AED)
    val CoralRose = Color(0xFFFF3D77)
    val EmeraldLive = Color(0xFF10B981)
    val AmberWarn = Color(0xFFF59E0B)
    val CrimsonErr = Color(0xFFF43F5E)
}

fun OmegaVisualTheme.isDark(): Boolean = true
fun OmegaVisualTheme.primaryText(): Color = Color(0xFFF8FAFC)
fun OmegaVisualTheme.mutedText(): Color = Color(0xFF8B90A0)
fun OmegaVisualTheme.accentColor(preset: OrbColorPreset): Color = Color(preset.primaryHex)

@Composable
fun omegaSwitchColors(theme: OmegaVisualTheme, preset: OrbColorPreset): SwitchColors {
    val accent = Color(preset.primaryHex)
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = accent,
        checkedBorderColor = accent,
        uncheckedThumbColor = Color(0xFF8E92A4),
        uncheckedTrackColor = Color(0xFF1C1C24),
        uncheckedBorderColor = Color(0xFF2E2E3A)
    )
}

@Composable
fun omegaTextFieldColors(theme: OmegaVisualTheme, preset: OrbColorPreset): TextFieldColors {
    val accent = Color(preset.primaryHex)
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = Color(0xFF262632),
        focusedContainerColor = Color(preset.surfaceHex),
        unfocusedContainerColor = Color(preset.backgroundHex),
        focusedTextColor = Color(0xFFF8FAFC),
        unfocusedTextColor = Color(0xFFE2E8F0),
        focusedLabelColor = accent,
        unfocusedLabelColor = Color(0xFF8B90A0),
        cursorColor = accent
    )
}

/**
 * Pure AMOLED Pitch Black (#000000) backdrop — ZERO aura light, ZERO radial glow blobs!
 */
@Composable
fun OmegaLiquidBackdrop(
    theme: OmegaVisualTheme,
    preset: OrbColorPreset,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = Color(preset.backgroundHex)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        content()
    }
}

/**
 * Clean Matte Dark Container with ultra-thin hairline border (Zero glow/aura).
 */
fun Modifier.omegaGlassCard(
    cornerRadius: Dp = 18.dp,
    theme: OmegaVisualTheme,
    preset: OrbColorPreset = OrbColorPreset.SOLAR_NOVA
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Color(preset.surfaceHex))
    .border(
        width = 1.dp,
        color = Color(preset.primaryHex).copy(alpha = 0.24f),
        shape = RoundedCornerShape(cornerRadius)
    )
