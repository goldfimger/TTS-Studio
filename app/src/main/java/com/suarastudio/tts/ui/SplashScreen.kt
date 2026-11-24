package com.suarastudio.tts.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(2550)
        visible = false
        delay(450)
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gemini_twinkle")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val starScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_scale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(450)),
        exit = fadeOut(animationSpec = tween(450))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = starScale
                                scaleY = starScale
                            }
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF4285F4).copy(alpha = glowAlpha * 0.5f),
                                    Color(0xFF9B72CB).copy(alpha = glowAlpha * 0.25f),
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Gemini Voice Studio",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
