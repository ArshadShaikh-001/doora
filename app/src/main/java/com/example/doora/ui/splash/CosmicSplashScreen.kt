package com.example.doora.ui.splash

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Color palette definitions according to design spec
private val VoidBlack = Color(0xFF020305)
private val DeepBlue = Color(0xFF071B3A)
private val ElectricBlue = Color(0xFF214DFF)
private val DarkEmerald = Color(0xFF063C32)
private val EmeraldHighlight = Color(0xFF0E5B4B)
private val DeepViolet = Color(0xFF28104A)
private val ElectricPurple = Color(0xFF7A35FF)
private val VioletCore = Color(0xFFE8D5FF)
private val CosmicGold = Color(0xFFD8C4A0)

/**
 * Pre-allocated cosmic particle representation to ensure 0 GC allocations per draw frame.
 */
private class Particle(
    val initialAngle: Float,
    val initialRadiusRatio: Float,
    val speedMultiplier: Float,
    val size: Float,
    val color: Color,
    val startProgress: Float
)

/**
 * Pre-allocated fast lightning streak trajectory definition.
 */
private class LightningStreak(
    val baseAngle: Float,
    val segmentCount: Int,
    val startRadiusRatio: Float,
    val strokeWidth: Float,
    val baseOpacity: Float,
    val timeStart: Float,
    val timeEnd: Float,
    val seed: Long
)

@Composable
fun CosmicSplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Check for reduced motion in system accessibility settings
    val isReducedMotion = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    if (isReducedMotion) {
        LaunchedEffect(Unit) {
            onSplashFinished()
        }
        return
    }

    // High-speed master animation clock: 0f -> 1f over 980ms
    val progress = remember { Animatable(0f) }
    var hapticTriggered by remember { mutableStateOf(false) }

    // Reusable Path instances for GPU canvas drawing (prevents memory allocation in draw loop)
    val ribbonPath1 = remember { Path() }
    val ribbonPath2 = remember { Path() }
    val ribbonPath3 = remember { Path() }
    val lightningPathOuter = remember { Path() }
    val lightningPathInner = remember { Path() }

    // Pre-allocate 64 particles with deterministic pseudo-random seeds
    // 32 particles are ALREADY in motion at frame 0 (startProgress = 0.0f)
    val particles = remember {
        val rand = Random(42)
        val colors = listOf(
            ElectricBlue,
            DeepBlue,
            DarkEmerald,
            EmeraldHighlight,
            ElectricPurple,
            DeepViolet,
            CosmicGold
        )
        List(64) { index ->
            val angle = (index.toFloat() / 64f) * (2f * PI.toFloat()) + rand.nextFloat() * 0.12f
            val radiusRatio = 0.70f + rand.nextFloat() * 0.55f
            val speed = 0.90f + rand.nextFloat() * 0.40f
            val size = 2.0f + rand.nextFloat() * 2.4f
            val color = colors[index % colors.size]
            // First 32 particles are already active at frame 0; remaining enter rapidly
            val startProg = if (index < 32) 0.0f else (rand.nextFloat() * 0.28f)
            Particle(angle, radiusRatio, speed, size, color, startProg)
        }
    }

    // Pre-allocate 16 high-speed directional lightning streaks converging toward center
    // Several streaks are ALREADY active and striking at frame 0 (timeStart = 0.0f)
    val lightningStreaks = remember {
        listOf(
            // Active from Frame 0: immediate lightning energy
            LightningStreak(baseAngle = 0.15f * PI.toFloat(), segmentCount = 6, startRadiusRatio = 0.88f, strokeWidth = 2.0f, baseOpacity = 0.95f, timeStart = 0.00f, timeEnd = 0.28f, seed = 101L),
            LightningStreak(baseAngle = 0.72f * PI.toFloat(), segmentCount = 7, startRadiusRatio = 0.95f, strokeWidth = 2.2f, baseOpacity = 0.90f, timeStart = 0.00f, timeEnd = 0.32f, seed = 202L),
            LightningStreak(baseAngle = 1.35f * PI.toFloat(), segmentCount = 6, startRadiusRatio = 0.82f, strokeWidth = 1.8f, baseOpacity = 0.95f, timeStart = 0.00f, timeEnd = 0.26f, seed = 303L),
            LightningStreak(baseAngle = 1.82f * PI.toFloat(), segmentCount = 7, startRadiusRatio = 0.92f, strokeWidth = 2.0f, baseOpacity = 0.90f, timeStart = 0.00f, timeEnd = 0.30f, seed = 404L),

            // Rapid early wave (0.06s - 0.45s)
            LightningStreak(baseAngle = 0.45f * PI.toFloat(), segmentCount = 6, startRadiusRatio = 0.85f, strokeWidth = 2.2f, baseOpacity = 0.95f, timeStart = 0.06f, timeEnd = 0.36f, seed = 505L),
            LightningStreak(baseAngle = 1.05f * PI.toFloat(), segmentCount = 7, startRadiusRatio = 0.90f, strokeWidth = 1.9f, baseOpacity = 0.90f, timeStart = 0.10f, timeEnd = 0.40f, seed = 606L),
            LightningStreak(baseAngle = 1.60f * PI.toFloat(), segmentCount = 6, startRadiusRatio = 0.80f, strokeWidth = 2.4f, baseOpacity = 0.95f, timeStart = 0.14f, timeEnd = 0.44f, seed = 707L),
            LightningStreak(baseAngle = 0.90f * PI.toFloat(), segmentCount = 8, startRadiusRatio = 0.94f, strokeWidth = 2.1f, baseOpacity = 0.90f, timeStart = 0.18f, timeEnd = 0.48f, seed = 808L),

            // Mid convergence wave (0.22s - 0.65s)
            LightningStreak(baseAngle = 0.30f * PI.toFloat(), segmentCount = 7, startRadiusRatio = 0.86f, strokeWidth = 2.3f, baseOpacity = 0.95f, timeStart = 0.22f, timeEnd = 0.52f, seed = 909L),
            LightningStreak(baseAngle = 1.45f * PI.toFloat(), segmentCount = 6, startRadiusRatio = 0.88f, strokeWidth = 2.0f, baseOpacity = 0.92f, timeStart = 0.26f, timeEnd = 0.56f, seed = 1010L),
            LightningStreak(baseAngle = 0.60f * PI.toFloat(), segmentCount = 8, startRadiusRatio = 0.92f, strokeWidth = 2.5f, baseOpacity = 0.95f, timeStart = 0.30f, timeEnd = 0.62f, seed = 1111L),
            LightningStreak(baseAngle = 1.20f * PI.toFloat(), segmentCount = 7, startRadiusRatio = 0.84f, strokeWidth = 1.8f, baseOpacity = 0.90f, timeStart = 0.35f, timeEnd = 0.66f, seed = 1212L),

            // High acceleration pull wave (0.40s - 0.85s)
            LightningStreak(baseAngle = 1.75f * PI.toFloat(), segmentCount = 6, startRadiusRatio = 0.90f, strokeWidth = 2.4f, baseOpacity = 0.95f, timeStart = 0.40f, timeEnd = 0.72f, seed = 1313L),
            LightningStreak(baseAngle = 0.05f * PI.toFloat(), segmentCount = 7, startRadiusRatio = 0.85f, strokeWidth = 2.2f, baseOpacity = 0.95f, timeStart = 0.46f, timeEnd = 0.76f, seed = 1414L),
            LightningStreak(baseAngle = 0.80f * PI.toFloat(), segmentCount = 8, startRadiusRatio = 0.95f, strokeWidth = 2.6f, baseOpacity = 0.98f, timeStart = 0.52f, timeEnd = 0.82f, seed = 1515L),
            LightningStreak(baseAngle = 1.50f * PI.toFloat(), segmentCount = 7, startRadiusRatio = 0.88f, strokeWidth = 2.5f, baseOpacity = 0.98f, timeStart = 0.56f, timeEnd = 0.86f, seed = 1616L)
        )
    }

    // Drive the master animation with high-speed timing (980ms total)
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 980,
                easing = LinearEasing
            )
        )
        onSplashFinished()
    }

    // Current normalized time t in [0f, 1f]
    val t = progress.value

    // Trigger subtle crisp haptic feedback at peak gravitational compression
    if (t >= 0.84f && !hapticTriggered) {
        hapticTriggered = true
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }

    // Immediate smooth transition into Doora Tasks (0.90f -> 1.0f)
    val splashAlpha = if (t >= 0.90f) {
        (1f - ((t - 0.90f) / 0.10f)).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .graphicsLayer {
                alpha = splashAlpha
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = hypot(center.x, center.y)

            // Layer 1: Pure Cosmic Void Base (predominant near-black)
            drawRect(VoidBlack)

            // Gravitational pull factor: starts active, accelerates rapidly
            val pullProgress = (t / 0.88f).coerceIn(0f, 1f)
            val gravPull = pullProgress.pow(2.7f)

            // ==========================================
            // Layer 2: Deep Blue Energy (ACTIVE FROM FRAME 0)
            // ==========================================
            if (t <= 0.90f) {
                // Active immediately from t = 0f, fades out smoothly as collapse finishes
                val blueFadeOut = if (t > 0.75f) (1f - ((t - 0.75f) / 0.15f)).coerceIn(0f, 1f) else 1f
                val blueAlpha = (0.50f + (t * 0.35f).coerceAtMost(0.25f)) * blueFadeOut

                val currentOuterRadius = maxRadius * (1f - gravPull * 0.92f)
                val rotationAngle = (t * 2.8f) + (gravPull * 3.5f) // energetic rotation

                for (i in 0..3) {
                    val baseArmAngle = (i.toFloat() * (PI.toFloat() / 2f)) + rotationAngle
                    val armRadius = currentOuterRadius * (0.75f + (i * 0.08f))
                    val armCenter = Offset(
                        center.x + cos(baseArmAngle) * (currentOuterRadius * 0.32f * (1f - gravPull)),
                        center.y + sin(baseArmAngle) * (currentOuterRadius * 0.32f * (1f - gravPull))
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ElectricBlue.copy(alpha = blueAlpha * 0.50f),
                                DeepBlue.copy(alpha = blueAlpha * 0.30f),
                                Color.Transparent
                            ),
                            center = armCenter,
                            radius = armRadius.coerceAtLeast(10f)
                        ),
                        radius = armRadius.coerceAtLeast(10f),
                        center = armCenter,
                        blendMode = BlendMode.Screen
                    )
                }
            }

            // ==========================================
            // Layer 3: Dark Emerald Aurora (ACTIVE FROM FRAME 0)
            // ==========================================
            if (t <= 0.90f) {
                val greenFadeOut = if (t > 0.75f) (1f - ((t - 0.75f) / 0.15f)).coerceIn(0f, 1f) else 1f
                val greenAlpha = (0.45f + (t * 0.25f).coerceAtMost(0.20f)) * greenFadeOut

                drawAuroraRibbon(
                    path = ribbonPath1,
                    center = center,
                    maxRadius = maxRadius,
                    gravPull = gravPull,
                    phase = t * 5.5f,
                    baseAngle = 0.2f * PI.toFloat(),
                    alpha = greenAlpha,
                    color = DarkEmerald,
                    highlight = EmeraldHighlight
                )

                drawAuroraRibbon(
                    path = ribbonPath2,
                    center = center,
                    maxRadius = maxRadius,
                    gravPull = gravPull,
                    phase = t * 5.5f + 1.8f,
                    baseAngle = 1.1f * PI.toFloat(),
                    alpha = greenAlpha * 0.85f,
                    color = DarkEmerald,
                    highlight = EmeraldHighlight
                )

                drawAuroraRibbon(
                    path = ribbonPath3,
                    center = center,
                    maxRadius = maxRadius,
                    gravPull = gravPull,
                    phase = t * 6.0f + 3.2f,
                    baseAngle = 1.7f * PI.toFloat(),
                    alpha = greenAlpha * 0.70f,
                    color = DarkEmerald,
                    highlight = EmeraldHighlight
                )
            }

            // ==========================================
            // Layer 4: Rapid Purple / Violet Lightning (ACTIVE FROM FRAME 0)
            // High speed, multiple simultaneous streaks, rapid convergence
            // ==========================================
            lightningStreaks.forEach { streak ->
                if (t in streak.timeStart..streak.timeEnd) {
                    val streakDuration = streak.timeEnd - streak.timeStart
                    val streakProg = ((t - streak.timeStart) / streakDuration).coerceIn(0f, 1f)

                    // Fast fade-in, peak, fast fade-out
                    val streakAlpha = sin(streakProg * PI.toFloat()).coerceIn(0f, 1f) * streak.baseOpacity

                    drawFastLightningStreak(
                        outerPath = lightningPathOuter,
                        innerPath = lightningPathInner,
                        streak = streak,
                        streakProg = streakProg,
                        center = center,
                        maxRadius = maxRadius,
                        gravPull = gravPull,
                        t = t,
                        alpha = streakAlpha
                    )
                }
            }

            // ==========================================
            // Layer 5: Cosmic Particles & Inward Streaks (ACTIVE FROM FRAME 0)
            // High energy inward acceleration
            // ==========================================
            if (t <= 0.92f) {
                particles.forEach { p ->
                    if (t >= p.startProgress) {
                        val duration = (0.88f - p.startProgress).coerceAtLeast(0.1f)
                        val pTime = ((t - p.startProgress) / duration).coerceIn(0f, 1f)
                        val pPull = (pTime * p.speedMultiplier).coerceIn(0f, 1f).pow(2.5f)

                        val currentDist = (maxRadius * p.initialRadiusRatio) * (1f - pPull)

                        if (currentDist > 4f) {
                            val pAngle = p.initialAngle + (gravPull * 0.8f) // dynamic spiral drift
                            val posX = center.x + cos(pAngle) * currentDist
                            val posY = center.y + sin(pAngle) * currentDist

                            // Stretched streak tail oriented toward center
                            val streakLength = (10f + gravPull * 65f * p.speedMultiplier).coerceIn(8f, 120f)
                            val tailX = center.x + cos(pAngle) * (currentDist + streakLength)
                            val tailY = center.y + sin(pAngle) * (currentDist + streakLength)

                            val pAlpha = (1f - gravPull * 0.35f).coerceIn(0.2f, 1f)

                            // Directional inward light streak
                            drawLine(
                                color = p.color.copy(alpha = pAlpha * 0.80f),
                                start = Offset(tailX, tailY),
                                end = Offset(posX, posY),
                                strokeWidth = p.size * (1f - gravPull * 0.3f),
                                cap = StrokeCap.Round
                            )

                            // Particle head glow
                            drawCircle(
                                color = VioletCore.copy(alpha = pAlpha * 0.95f),
                                radius = (p.size * 0.80f),
                                center = Offset(posX, posY)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // Layer 6: Gravitational Singularity & Final Collapse (0.78s -> 0.90s)
            // Rapid convergence into central compression of light
            // ==========================================
            if (t in 0.78f..0.91f) {
                val collapseProg = ((t - 0.78f) / 0.12f).coerceIn(0f, 1f)

                // Compression radius rapidly collapses from 45dp down to 0dp
                val collapseRadius = (45f * (1f - collapseProg).pow(2.4f)).coerceAtLeast(0f)

                if (collapseRadius > 0.5f) {
                    val coreGlowAlpha = sin(collapseProg * PI.toFloat()).coerceIn(0f, 1f)

                    // Compressed halo ring around central gravitational core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                VoidBlack,
                                DeepViolet.copy(alpha = coreGlowAlpha * 0.85f),
                                ElectricPurple.copy(alpha = coreGlowAlpha * 0.95f),
                                ElectricBlue.copy(alpha = coreGlowAlpha * 0.90f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = collapseRadius * 1.6f
                        ),
                        radius = collapseRadius * 1.6f,
                        center = center,
                        blendMode = BlendMode.Screen
                    )

                    // Ultra-dense singularity pinpoint
                    drawCircle(
                        color = VioletCore.copy(alpha = coreGlowAlpha),
                        radius = (collapseRadius * 0.25f).coerceAtLeast(1f),
                        center = center
                    )
                }
            }
        }
    }
}

/**
 * Renders an organic, fluid aurora ribbon that undulates and gets drawn inward.
 */
private fun DrawScope.drawAuroraRibbon(
    path: Path,
    center: Offset,
    maxRadius: Float,
    gravPull: Float,
    phase: Float,
    baseAngle: Float,
    alpha: Float,
    color: Color,
    highlight: Color
) {
    path.reset()

    val segments = 6
    val contraction = 1f - gravPull * 0.92f

    var isFirst = true
    for (s in 0..segments) {
        val u = s.toFloat() / segments.toFloat()
        val r = (maxRadius * (0.85f - u * 0.75f) * contraction).coerceAtLeast(0f)
        val waveOffset = sin(phase + u * 4.5f) * (32f * (1f - gravPull))
        val angle = baseAngle + u * 1.2f + (waveOffset / (r + 1f)) + (gravPull * 1.5f)

        val x = center.x + cos(angle) * (r + waveOffset)
        val y = center.y + sin(angle) * (r + waveOffset)

        if (isFirst) {
            path.moveTo(x, y)
            isFirst = false
        } else {
            path.lineTo(x, y)
        }
    }

    // Outer soft aura
    drawPath(
        path = path,
        color = color.copy(alpha = alpha * 0.50f),
        style = Stroke(
            width = 36f * (1f - gravPull * 0.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = BlendMode.Screen
    )

    // Inner bright fluid core
    drawPath(
        path = path,
        color = highlight.copy(alpha = alpha * 0.85f),
        style = Stroke(
            width = 15f * (1f - gravPull * 0.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = BlendMode.Screen
    )
}

/**
 * Draws a rapid, high-speed electric violet/purple lightning streak converging toward center.
 */
private fun DrawScope.drawFastLightningStreak(
    outerPath: Path,
    innerPath: Path,
    streak: LightningStreak,
    streakProg: Float,
    center: Offset,
    maxRadius: Float,
    gravPull: Float,
    t: Float,
    alpha: Float
) {
    outerPath.reset()
    innerPath.reset()

    // High frequency electric crackle micro-jumps (45Hz update rate)
    val crackleStep = (t * 45f).toInt()
    val pseudoRandomSeed = streak.seed + crackleStep

    // High speed inward movement:
    // Head moves from startRadius down to center with rapid acceleration
    val accelProg = streakProg.pow(1.6f)
    val rOuter = maxRadius * streak.startRadiusRatio * (1f - gravPull * 0.65f)
    val rHead = rOuter * (1f - accelProg).coerceAtLeast(0.02f)
    // Streak spans a dynamic length following the head
    val spanLength = maxRadius * 0.32f * (1f - streakProg * 0.5f)
    val rTail = (rHead + spanLength).coerceAtMost(rOuter)

    val segCount = streak.segmentCount
    val deltaR = (rTail - rHead) / segCount.toFloat()

    val baseAngle = streak.baseAngle + (gravPull * 0.5f)
    val perpAngle = baseAngle + (PI.toFloat() / 2f)

    var currX = center.x + cos(baseAngle) * rTail
    var currY = center.y + sin(baseAngle) * rTail

    outerPath.moveTo(currX, currY)
    innerPath.moveTo(currX, currY)

    for (s in 1..segCount) {
        val currR = rTail - (s * deltaR)
        val segProgress = s.toFloat() / segCount.toFloat()

        // Perpendicular jagged jitter diminishing near the center
        val jitterAmp = (1f - segProgress) * 26f * (1f - gravPull * 0.6f)
        val noiseVal = sin((pseudoRandomSeed + s * 97).toDouble()).toFloat()

        val nextX = center.x + cos(baseAngle) * currR + cos(perpAngle) * (noiseVal * jitterAmp)
        val nextY = center.y + sin(baseAngle) * currR + sin(perpAngle) * (noiseVal * jitterAmp)

        outerPath.lineTo(nextX, nextY)
        innerPath.lineTo(nextX, nextY)
    }

    // Outer purple glow stroke
    drawPath(
        path = outerPath,
        color = ElectricPurple.copy(alpha = alpha * 0.60f),
        style = Stroke(
            width = streak.strokeWidth * 3.2f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Miter
        ),
        blendMode = BlendMode.Screen
    )

    // Inner bright white-violet electric core stroke
    drawPath(
        path = innerPath,
        color = VioletCore.copy(alpha = alpha * 0.95f),
        style = Stroke(
            width = streak.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Miter
        )
    )
}
