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

val MidnightNavy = Color(0xFF080E1D)
val DeepBlue = Color(0xFF12233F)
val ElectricBlue = Color(0xFF2563FF)
val NeonCyan = Color(0xFF00E5FF)
val RoyalViolet = Color(0xFF8B5CF6)
val IceWhite = Color(0xFFE8F4FF)

object OmegaColors {
    val PureBlack = MidnightNavy
    val CarbonSurface = DeepBlue
    val CarbonElevated = Color(0xFF162B4D)
    val SubtleBorder = Color(0xFF1E3A63)
    val HairlineDivider = Color(0xFF1A3154)

    val VelvetTop = MidnightNavy
    val VelvetMid = Color(0xFF0B152B)
    val VelvetBottom = MidnightNavy

    val ElectricCyan = NeonCyan
    val RoyalIndigo = RoyalViolet
    val CoralRose = Color(0xFFFF3D77)
    val EmeraldLive = Color(0xFF10B981)
    val AmberWarn = Color(0xFFF59E0B)
    val CrimsonErr = Color(0xFFF43F5E)
}

fun OmegaVisualTheme.isDark(): Boolean = true
fun OmegaVisualTheme.primaryText(): Color = IceWhite
fun OmegaVisualTheme.mutedText(): Color = Color(0xFF9FB4D0)
fun OmegaVisualTheme.accentColor(preset: OrbColorPreset): Color = Color(preset.primaryHex)
fun OmegaVisualTheme.deepGlassSurface(): Color = DeepBlue.copy(alpha = 0.88f)

@Composable
fun omegaSwitchColors(theme: OmegaVisualTheme, preset: OrbColorPreset): SwitchColors {
    val accent = NeonCyan
    return SwitchDefaults.colors(
        checkedThumbColor = IceWhite,
        checkedTrackColor = ElectricBlue,
        checkedBorderColor = NeonCyan.copy(alpha = 0.7f),
        uncheckedThumbColor = Color(0xFF9FB4D0),
        uncheckedTrackColor = DeepBlue,
        uncheckedBorderColor = Color(0xFF2B4670)
    )
}

@Composable
fun omegaTextFieldColors(theme: OmegaVisualTheme, preset: OrbColorPreset): TextFieldColors {
    val accent = NeonCyan
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = ElectricBlue.copy(alpha = 0.45f),
        focusedContainerColor = DeepBlue,
        unfocusedContainerColor = MidnightNavy,
        focusedTextColor = IceWhite,
        unfocusedTextColor = IceWhite.copy(alpha = 0.9f),
        focusedLabelColor = accent,
        unfocusedLabelColor = Color(0xFF9FB4D0),
        cursorColor = accent
    )
}

/**
 * V110.1 Unified Midnight Navy (#080E1D) backdrop across all screens.
 */
@Composable
fun OmegaLiquidBackdrop(
    theme: OmegaVisualTheme,
    preset: OrbColorPreset,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightNavy)
    ) {
        content()
    }
}

/**
 * V110.1 Lightweight Glass Card with uniform 16dp-18dp corners and high-contrast border.
 */
fun Modifier.omegaGlassCard(
    cornerRadius: Dp = 18.dp,
    theme: OmegaVisualTheme,
    preset: OrbColorPreset = OrbColorPreset.SOLAR_NOVA
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(DeepBlue.copy(alpha = 0.86f))
    .border(
        width = 1.dp,
        color = NeonCyan.copy(alpha = 0.28f),
        shape = RoundedCornerShape(cornerRadius)
    )
