package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.OrbColorPreset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Borderless 3D Dotted Particle Wave Mesh Sphere (v20 AMOLED):
 * Projects ~1,000+ 3D spherical lattice points with organic wave displacement,
 * perspective depth scaling, and smooth 3-stop gradient coloring on Pure Black.
 * Matches both Solar Nova (orange/magenta/violet) & Arctic Blue (cyan/blue/violet).
 */
@Composable
fun ParticleAiOrb(
    size: Dp = 230.dp,
    isListening: Boolean = false,
    audioAmplitude: Float = 0f,
    preset: OrbColorPreset = OrbColorPreset.SOLAR_NOVA,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "dotSphere")
    val smoothAmp by animateFloatAsState(
        targetValue = audioAmplitude.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "amp"
    )
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (isListening) 5200 else 10500, easing = LinearEasing)),
        label = "phase"
    )

    val cPrimary = Color(preset.primaryHex)     // Outer crest highlight (Orange or Cyan)
    val cSecondary = Color(preset.secondaryHex) // Mid wave transition (Magenta or Sky Blue)
    val cAccent = Color(preset.accentHex)       // Deep valley core (Indigo/Violet)

    val isMini = size.value < 60f

    val reduced = LocalReducedRendering.current
    val latSteps = if (isMini) 8 else if (reduced) 12 else 28
    val lonSteps = if (isMini) 12 else if (reduced) 20 else 42
    val thetaValues = remember(latSteps) { FloatArray(latSteps) { (PI * it / latSteps).toFloat() } }
    val phiValues = remember(lonSteps) { FloatArray(lonSteps) { (2.0 * PI * it / lonSteps).toFloat() } }
    val thetaSin = remember(thetaValues) { FloatArray(latSteps) { sin(thetaValues[it]) } }
    val thetaCos = remember(thetaValues) { FloatArray(latSteps) { cos(thetaValues[it]) } }
    val phiSin = remember(phiValues) { FloatArray(lonSteps) { sin(phiValues[it]) } }
    val phiCos = remember(phiValues) { FloatArray(lonSteps) { cos(phiValues[it]) } }
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val baseR = this.size.minDimension * 0.37f

        // Zero aura light: crisp 3D dotted sphere directly on pure AMOLED black

        val waveAmp = (0.14f + smoothAmp * 0.16f)

        val rotY = phase
        val rotX = 0.42f * sin(phase * 0.5f)
        val cosY = cos(rotY); val sinY = sin(rotY)
        val cosX = cos(rotX); val sinX = sin(rotX)

        val baseDotPx = if (isMini) 1.3.dp.toPx() else (1.55.dp.toPx() * (size.value / 230f).coerceIn(0.65f, 1.25f))

        for (i in 1 until latSteps) {
            val theta = thetaValues[i]
            val sinT = thetaSin[i]
            val cosT = thetaCos[i]

            for (j in 0 until lonSteps) {
                val phi = phiValues[j]

                // Organic multi-frequency 3D surface wave displacement
                val w1 = sin(3f * theta + phase * 2f) * cos(4f * phi - phase)
                val w2 = cos(5f * phi + 2f * theta + phase * 1.5f) * 0.45f
                val disp = (w1 + w2) * waveAmp
                val r = baseR * (1f + disp)

                // Spherical -> Cartesian
                val x0 = r * sinT * phiCos[j]
                val y0 = r * cosT
                val z0 = r * sinT * phiSin[j]

                // Rotate around Y then X
                val x1 = x0 * cosY - z0 * sinY
                val z1 = x0 * sinY + z0 * cosY
                val y2 = y0 * cosX - z1 * sinX
                val z2 = y0 * sinX + z1 * cosX

                // Perspective projection
                val depthNorm = ((z2 / (baseR * 1.35f)) + 1f) * 0.5f // 0 (back) .. 1 (front)
                val perspective = 0.80f + 0.34f * depthNorm
                val sx = cx + x1 * perspective
                val sy = cy + y2 * perspective

                // Color gradient by wave height & depth (crests = primary/bright, valleys = deep violet)
                val crestFactor = ((disp / (waveAmp * 1.45f)) + 0.5f).coerceIn(0f, 1f)
                val dotColor = if (crestFactor > 0.52f) {
                    lerp(cSecondary, cPrimary, ((crestFactor - 0.52f) / 0.48f).coerceIn(0f, 1f))
                } else {
                    lerp(cAccent, cSecondary, (crestFactor / 0.52f).coerceIn(0f, 1f))
                }

                val alpha = (0.14f + 0.86f * depthNorm * depthNorm).coerceIn(0.12f, 1f)
                val dotR = baseDotPx * (0.48f + 0.78f * depthNorm)

                drawCircle(
                    color = dotColor.copy(alpha = alpha),
                    radius = dotR,
                    center = Offset(sx, sy)
                )
            }
        }
    }
}
