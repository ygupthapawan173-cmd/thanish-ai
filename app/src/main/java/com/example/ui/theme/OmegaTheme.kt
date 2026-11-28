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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** V111 Liquid Sapphire palette. Frosted gradient surfaces, no expensive live blur. */
object OmegaColors {
    val DeepNavy = Color(0xFF254E88)
    val SurfaceNavy = Color(0xFF285181)
    val ElevatedNavy = Color(0xFF34659A)
    val BorderBlue = Color(0xFF9EC9EF)

    val NeonBlue = Color(0xFF174FBD)
    val NeonCyan = Color(0xFFDDF8FF)
    val IceWhite = Color(0xFFE8F4FF)
    val MutedSlate = Color(0xFFDFEFFF)

    val EmeraldLive = Color(0xFF10B981)
    val AmberWarn = Color(0xFFF59E0B)
    val CrimsonErr = Color(0xFFF43F5E)
}

@Composable
fun omegaSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = OmegaColors.IceWhite,
    checkedTrackColor = OmegaColors.NeonBlue,
    checkedBorderColor = OmegaColors.NeonCyan,
    uncheckedThumbColor = OmegaColors.MutedSlate,
    uncheckedTrackColor = OmegaColors.ElevatedNavy,
    uncheckedBorderColor = OmegaColors.BorderBlue
)

@Composable
fun omegaTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OmegaColors.NeonCyan,
    unfocusedBorderColor = OmegaColors.BorderBlue,
    focusedContainerColor = OmegaColors.SurfaceNavy,
    unfocusedContainerColor = OmegaColors.DeepNavy,
    focusedTextColor = OmegaColors.IceWhite,
    unfocusedTextColor = OmegaColors.IceWhite.copy(alpha = 0.9f),
    focusedLabelColor = OmegaColors.NeonCyan,
    unfocusedLabelColor = OmegaColors.MutedSlate,
    cursorColor = OmegaColors.NeonCyan
)

@Composable
fun OmegaNavyBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(OmegaColors.DeepNavy, Color(0xFF3C6FA9), Color(0xFF6CA5D4))
                )
            )
    ) {
        content()
    }
}

fun Modifier.omegaGlassCard(
    cornerRadius: Dp = 18.dp,
    highlighted: Boolean = false
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Brush.linearGradient(
        if (highlighted) listOf(Color(0xFF3B6CA2), Color(0xFF285281))
        else listOf(Color(0xFF325F92), Color(0xFF244C7C))
    ))
    .border(
        width = 1.dp,
        color = if (highlighted) OmegaColors.NeonCyan.copy(alpha = 0.55f) else OmegaColors.BorderBlue.copy(alpha = 0.48f),
        shape = RoundedCornerShape(cornerRadius)
    )
