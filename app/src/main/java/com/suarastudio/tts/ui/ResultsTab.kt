package com.suarastudio.tts.ui

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suarastudio.tts.audio.PlaybackState
import com.suarastudio.tts.audio.SavedAudioFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ResultsTab(
    savedFiles: List<SavedAudioFile>,
    activeSavedUri: Uri?,
    playbackState: PlaybackState,
    onRefresh: () -> Unit,
    onPlayFile: (SavedAudioFile) -> Unit,
    onEditFile: (SavedAudioFile) -> Unit,
    onDeleteFile: (SavedAudioFile) -> Unit,
    modifier: Modifier = Modifier
) {
    var infoTarget by remember { mutableStateOf<SavedAudioFile?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Artifacts Audio",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${savedFiles.size} file .wav tersimpan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Muat ulang artifacts"
                )
            }
        }

        if (savedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Belum ada file artifacts",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Buat suara di tab Projek lalu tekan Simpan (.wav).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedFiles, key = { it.uri.toString() }) { file ->
                    val isThisPlaying = activeSavedUri == file.uri && playbackState.isPlaying
                    var menuExpanded by remember { mutableStateOf(false) }

                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(onClick = { onPlayFile(file) }) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isThisPlaying) "Jeda" else "Putar"
                                    )
                                }

                                FilledTonalIconButton(onClick = { onEditFile(file) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit audio ${file.displayName}"
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatDurationMs(file.durationMs)} · ${formatBytes(file.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "Menu opsi file"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Info") },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Info, contentDescription = null)
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                infoTarget = file
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Delete, contentDescription = null)
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onDeleteFile(file)
                                            }
                                        )
                                    }
                                }
                            }

                            if (isThisPlaying) {
                                AudioWaveVisualizer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    infoTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text("Informasi File Audio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileInfoLine("Nama File", target.displayName)
                    FileInfoLine("Direktori", target.relativePath)
                    FileInfoLine("Ukuran", "${formatBytes(target.sizeBytes)} (${target.sizeBytes} bytes)")
                    FileInfoLine("Durasi", formatDurationMs(target.durationMs))
                    FileInfoLine("Format", "WAV · 24 kHz · 16-bit Mono PCM")
                    FileInfoLine("Waktu Simpan", formatDateTime(target.dateAddedMillis))
                    FileInfoLine("URI", target.uri.toString())
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun AudioWaveVisualizer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave_visualizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val barColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val barCount = 24
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        for (i in 0 until barCount) {
            val wave = (sin(phase + i * 0.55f) + 1f) * 0.5f
            val barHeight = size.height * (0.2f + 0.78f * wave)
            val left = i * (barWidth + gap)
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun FileInfoLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

private fun formatBytes(bytes: Long): String {
    return if (bytes >= 1024 * 1024) {
        String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
    } else {
        String.format(Locale.US, "%.1f KB", bytes / 1024f)
    }
}

private fun formatDateTime(millis: Long): String {
    if (millis <= 0L) return "-"
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID"))
    return sdf.format(Date(millis))
}
