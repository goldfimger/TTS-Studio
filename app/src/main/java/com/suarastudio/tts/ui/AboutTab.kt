package com.suarastudio.tts.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suarastudio.tts.R
import com.suarastudio.tts.data.UserSettings

fun resolveGeminiAccountDisplay(
    uiState: TtsUiState,
    settings: UserSettings
): Pair<String, String> {
    val key = settings.apiKey.trim()
    if (key.isEmpty()) {
        return "Belum Terhubung" to "API Key belum diisi di Pengaturan"
    }
    if (uiState.isApiConnected == false) {
        return "Terputus / Tidak Valid" to "Periksa API Key atau koneksi internet"
    }

    val jenisGemini = uiState.detectedGeminiSummary.ifBlank {
        when {
            settings.modelId.contains("3.8", ignoreCase = true) -> "Gemini 3.8 TTS"
            settings.modelId.contains("2.5", ignoreCase = true) -> "Gemini 2.5 TTS"
            else -> settings.modelId
        }
    }

    val tierNote = when {
        settings.lastQuotaAlert.contains("Free Tier", ignoreCase = true) ||
            settings.lastQuotaAlert.contains("free_tier", ignoreCase = true) ->
            "Free Tier (Terdeteksi dari API)"
        settings.dailyRequestLimit <= 20 -> "Free Tier (Batas 20 req/hari)"
        settings.dailyRequestLimit >= 200 -> "Paid / Tier 1+ (${settings.dailyRequestLimit} req/hari)"
        key.startsWith("AQ.") -> "Aktif · AI Studio Key (AQ)"
        key.startsWith("AIza") -> "Aktif · Google Cloud Key (AIza)"
        else -> "Aktif · Gemini API Key"
    }

    return jenisGemini to tierNote
}

@Composable
fun AboutTab(
    uiState: TtsUiState,
    settings: UserSettings,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var showDonateModal by remember { mutableStateOf(false) }

    val (jenisGemini, tierStatus) = resolveGeminiAccountDisplay(uiState, settings)
    val (statusDotColor, statusHeadline) = when {
        settings.apiKey.isBlank() -> Color(0xFFDC2626) to "Belum Diisi"
        uiState.isApiConnected == true -> Color(0xFF16A34A) to "Terhubung (Connected)"
        uiState.isApiConnected == false -> Color(0xFFDC2626) to "Terputus (Disconnected)"
        else -> MaterialTheme.colorScheme.outline to "Memeriksa API Key..."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedGeminiAppIcon(modifier = Modifier.size(76.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Tentang Aplikasi",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Gemini Voice Studio",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Text-To Speech Gemini TTS API",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Status & Jenis Gemini Akun dari API Key (di atas Informasi Developer)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status & Jenis Gemini Akun",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = statusDotColor.copy(alpha = 0.12f),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Text(
                                text = statusHeadline,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusDotColor
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Jenis Gemini: $jenisGemini",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Tier / Paket API Key: $tierStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.detectedTtsModels.isNotEmpty()) {
                        Text(
                            text = "Akses Model TTS (${uiState.detectedTtsModels.size}): ${uiState.detectedTtsModels.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (settings.apiKey.isNotBlank()) {
                        Text(
                            text = "Model aktif: ${settings.modelId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Pure Monet button with Gemini sparkle logo
                Button(
                    onClick = { uriHandler.openUri("https://aistudio.google.com/billing") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GeminiSparkleVector(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upgrade Gemini", maxLines = 1)
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Informasi Developer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Goldfimger",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { uriHandler.openUri("https://github.com/goldfimger") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GitHub", maxLines = 1)
                    }

                    Button(
                        onClick = { showDonateModal = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Donate", maxLines = 1)
                    }
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ucapan Terima Kasih",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Terima kasih sebesar-besarnya kepada Google AI Studio, Gemini, dan GitHub yang telah mendukung pengembangan aplikasi ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Google AI Studio · Gemini · GitHub",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showDonateModal) {
        Dialog(
            onDismissRequest = {
                // Intentionally blocked: user must tap the X button to close
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Donate QRIS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.Black
                            )
                            IconButton(onClick = { showDonateModal = false }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Tutup jendela Donate",
                                    tint = Color.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Image(
                            painter = painterResource(id = R.drawable.qris_donate),
                            contentDescription = "QRIS Pembayaran Nasional Developer Aplikasi",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiSparkleVector(modifier: Modifier = Modifier) {
    val tint = LocalContentColor.current
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.45f
        val path = Path().apply {
            moveTo(cx, cy - r)
            cubicTo(cx, cy - r * 0.38f, cx + r * 0.38f, cy, cx + r, cy)
            cubicTo(cx + r * 0.38f, cy, cx, cy + r * 0.38f, cx, cy + r)
            cubicTo(cx, cy + r * 0.38f, cx - r * 0.38f, cy, cx - r, cy)
            cubicTo(cx - r * 0.38f, cy, cx, cy - r * 0.38f, cx, cy - r)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
private fun AnimatedGeminiAppIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gemini_icon_motion")
    val rotation by transition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gemini_rotate"
    )
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gemini_scale"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gemini_glow"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 10.dp, end = 6.dp)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            val center = Offset(size.width * 0.46f, size.height * 0.44f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1BA1E3).copy(alpha = glowAlpha * 0.48f),
                        Color(0xFF9B72CB).copy(alpha = glowAlpha * 0.28f),
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

        Text(
            text = "TTS",
            color = Color.Black,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 7.dp, bottom = 5.dp)
        )
    }
}
