package com.suarastudio.tts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suarastudio.tts.audio.PlaybackState
import java.util.Locale

@Composable
fun AudioPlayerBar(
    playbackState: PlaybackState,
    isSavingWav: Boolean,
    downloadProgress: Int?,
    isDownloadDone: Boolean,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onSaveWav: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val duration = playbackState.durationMs.coerceAtLeast(1L)
            val progressFraction = (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

            Slider(
                value = if (playbackState.hasAudio) progressFraction else 0f,
                onValueChange = { fraction ->
                    if (playbackState.hasAudio) {
                        onSeek((fraction * duration).toLong())
                    }
                },
                enabled = playbackState.hasAudio,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        enabled = playbackState.hasAudio
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Jeda" else "Putar"
                        )
                    }

                    IconButton(
                        onClick = onStop,
                        enabled = playbackState.hasAudio
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Berhenti"
                        )
                    }

                    Text(
                        text = "${formatDuration(playbackState.positionMs)} / ${formatDuration(playbackState.durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onSaveWav,
                    enabled = playbackState.hasAudio && !isSavingWav
                ) {
                    when {
                        isSavingWav && downloadProgress != null -> {
                            Text("$downloadProgress%", maxLines = 1)
                        }
                        isDownloadDone -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Done", maxLines = 1)
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan (.wav)", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
