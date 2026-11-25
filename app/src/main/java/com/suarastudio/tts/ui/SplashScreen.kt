package com.suarastudio.tts.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    // Starts opaque at 1f immediately on frame 0 so the main UI never flashes before splash
    val screenAlpha = remember { Animatable(1f) }
    val shootingProgress = remember { Animatable(0f) }
    val impactRipple = remember { Animatable(0f) }
    val logoAppear = remember { Animatable(0f) }
    val titleAppear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Phase 1: Shooting star streaks diagonally down into center (0ms .. 760ms)
        shootingProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
        )

        // Phase 2: Impact burst + Gemini star materializes and title fades in
        launch {
            impactRipple.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoAppear.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(160)
            titleAppear.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }

        // Phase 3: Twinkling Gemini star hold up to ~2.55s, then smooth 450ms fade-out
        delay(1790)
        screenAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 450, easing = LinearEasing)
        )
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gemini_twinkle")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val starScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = screenAlpha.value }
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        // Fullscreen canvas for shooting star comet streak into center
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = shootingProgress.value
            if (t < 1f) {
                val endX = size.width * 0.5f
                val endY = size.height * 0.45f
                val startX = size.width * 0.94f
                val startY = size.height * 0.08f

                val curX = startX + (endX - startX) * t
                val curY = startY + (endY - startY) * t

                val tailT = (t - 0.34f).coerceAtLeast(0f)
                val tailX = startX + (endX - startX) * tailT
                val tailY = startY + (endY - startY) * tailT

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF1BA1E3).copy(alpha = 0.55f),
                            Color(0xFF9B72CB).copy(alpha = 0.88f),
                            Color.White
                        ),
                        start = Offset(tailX, tailY),
                        end = Offset(curX, curY)
                    ),
                    start = Offset(tailX, tailY),
                    end = Offset(curX, curY),
                    strokeWidth = 9f,
                    cap = StrokeCap.Round
                )

                // Comet head glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFF1BA1E3).copy(alpha = 0.75f),
                            Color.Transparent
                        ),
                        center = Offset(curX, curY),
                        radius = 34f
                    ),
                    radius = 34f,
                    center = Offset(curX, curY)
                )

                // Small 4-point mini star at comet head
                val miniR = 14f
                val miniPath = Path().apply {
                    moveTo(curX, curY - miniR)
                    cubicTo(curX, curY - miniR * 0.35f, curX + miniR * 0.35f, curY, curX + miniR, curY)
                    cubicTo(curX + miniR * 0.35f, curY, curX, curY + miniR * 0.35f, curX, curY + miniR)
                    cubicTo(curX, curY + miniR * 0.35f, curX - miniR * 0.35f, curY, curX - miniR, curY)
                    cubicTo(curX - miniR * 0.35f, curY, curX, curY - miniR * 0.35f, curX, curY - miniR)
                    close()
                }
                drawPath(path = miniPath, color = Color.White)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(128.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val appearScale = 0.45f + (0.55f * logoAppear.value)
                            val pulse = if (logoAppear.value >= 0.99f) starScale else 1f
                            scaleX = appearScale * pulse
                            scaleY = appearScale * pulse
                            alpha = logoAppear.value
                        }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Impact ripple ring right when shooting star lands
                    if (impactRipple.value in 0.01f..0.99f) {
                        val rippleRadius = size.minDimension * (0.25f + impactRipple.value * 0.52f)
                        drawCircle(
                            color = Color(0xFF1BA1E3).copy(alpha = (1f - impactRipple.value) * 0.45f),
                            radius = rippleRadius,
                            center = center
                        )
                    }

                    // Twinkling radial glow behind Gemini star
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4285F4).copy(alpha = glowAlpha * 0.55f),
                                Color(0xFF9B72CB).copy(alpha = glowAlpha * 0.32f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension * 0.55f
                        ),
                        radius = size.minDimension * 0.55f,
                        center = center
                    )

                    val r = size.minDimension * 0.34f
                    val starPath = Path().apply {
                        moveTo(center.x, center.y - r)
                        cubicTo(center.x, center.y - r * 0.38f, center.x + r * 0.38f, center.y, center.x + r, center.y)
                        cubicTo(center.x + r * 0.38f, center.y, center.x, center.y + r * 0.38f, center.x, center.y + r)
                        cubicTo(center.x, center.y + r * 0.38f, center.x - r * 0.38f, center.y, center.x - r, center.y)
                        cubicTo(center.x - r * 0.38f, center.y, center.x, center.y - r * 0.38f, center.x, center.y - r)
                        close()
                    }

                    drawPath(
                        path = starPath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1BA1E3),
                                Color(0xFF5489D6),
                                Color(0xFF9B72CB),
                                Color(0xFFD96570)
                            ),
                            start = Offset(center.x - r, center.y - r),
                            end = Offset(center.x + r, center.y + r)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Gemini Voice Studio",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAppear.value
                    translationY = (1f - titleAppear.value) * 18f
                }
            )
        }
    }
}
