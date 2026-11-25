package com.suarastudio.tts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suarastudio.tts.audio.MediaStoreSaver
import com.suarastudio.tts.audio.PlaybackState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorSheet(
    editorState: AudioEditorState,
    playbackState: PlaybackState,
    onTrim: (Float, Float) -> Unit,
    onCut: (Float, Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePlay: () -> Unit,
    onRequestSave: () -> Unit,
    onDismissSaveConfirm: () -> Unit,
    onConfirmSave: () -> Unit,
    onDiscard: () -> Unit
) {
    var selectionRange by remember(editorState.currentPcm.size, editorState.undoCount, editorState.redoCount) {
        mutableStateOf(0f..1f)
    }

    val startMs = (editorState.durationMs * selectionRange.start).toLong()
    val endMs = (editorState.durationMs * selectionRange.endInclusive).toLong()
    val hasValidSubRange = (selectionRange.endInclusive - selectionRange.start) > 0.02f &&
        (selectionRange.start > 0.005f || selectionRange.endInclusive < 0.995f)

    ModalBottomSheet(
        onDismissRequest = onDiscard,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Editor Audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = editorState.targetFile.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDiscard) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup editor"
                    )
                }
            }

            HorizontalDivider()

            // Waveform preview with selected region highlight & playhead
            val primaryColor = MaterialTheme.colorScheme.primary
            val mutedColor = MaterialTheme.colorScheme.outlineVariant
            val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            val playProgress = if (editorState.durationMs > 0 && playbackState.isPlaying) {
                (playbackState.positionMs.toFloat() / editorState.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                val selLeft = size.width * selectionRange.start
                val selRight = size.width * selectionRange.endInclusive
                drawRoundRect(
                    color = highlightBg,
                    topLeft = Offset(selLeft, 0f),
                    size = Size((selRight - selLeft).coerceAtLeast(2f), size.height),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                val bars = editorState.waveformBars
                val count = bars.size.coerceAtLeast(1)
                val gap = 2.dp.toPx()
                val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)

                for (i in 0 until count) {
                    val frac = (i + 0.5f) / count.toFloat()
                    val insideSelection = frac in selectionRange.start..selectionRange.endInclusive
                    val barH = (size.height * 0.85f * bars[i]).coerceAtLeast(6f)
                    val left = i * (barW + gap)
                    val top = (size.height - barH) / 2f
                    drawRoundRect(
                        color = if (insideSelection) primaryColor else mutedColor,
                        topLeft = Offset(left, top),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }

                if (playProgress > 0f) {
                    val x = size.width * playProgress
                    drawLine(
                        color = primaryColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 3f
                    )
                }
            }

            RangeSlider(
                value = selectionRange,
                onValueChange = { range ->
                    val minSpan = 0.02f
                    val s = range.start.coerceIn(0f, 1f - minSpan)
                    val e = range.endInclusive.coerceIn(s + minSpan, 1f)
                    selectionRange = s..e
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pilih: ${formatMillisPrecise(startMs)} – ${formatMillisPrecise(endMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Total: ${formatMillisPrecise(editorState.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Playback + Undo / Redo row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Jeda" else "Putar"
                        )
                    }
                    Text(
                        text = if (playbackState.isPlaying) "Memutar..." else "Putar hasil edit",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onUndo,
                        enabled = editorState.undoCount > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo")
                    }
                    OutlinedButton(
                        onClick = onRedo,
                        enabled = editorState.redoCount > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = "Redo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Redo")
                    }
                }
            }

            // Trim & Cut controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onTrim(selectionRange.start, selectionRange.endInclusive) },
                    enabled = hasValidSubRange,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Crop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trimming")
                }

                FilledTonalButton(
                    onClick = { onCut(selectionRange.start, selectionRange.endInclusive) },
                    enabled = hasValidSubRange,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cut")
                }
            }

            // Save button (opens Save vs Discard choice)
            Button(
                onClick = onRequestSave,
                enabled = !editorState.isSaving && editorState.currentPcm.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Audio")
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (editorState.showSaveConfirm) {
        val editedName = MediaStoreSaver.buildEditedFileName(editorState.targetFile.displayName)
        AlertDialog(
            onDismissRequest = onDismissSaveConfirm,
            title = { Text("Simpan Perubahan Audio?") },
            text = {
                Text("File hasil edit akan disimpan otomatis dengan nama:\n$editedName")
            },
            confirmButton = {
                Button(
                    onClick = onConfirmSave,
                    enabled = !editorState.isSaving
                ) {
                    Text(if (editorState.isSaving) "Menyimpan..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDiscard,
                    enabled = !editorState.isSaving,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard")
                }
            }
        )
    }
}

private fun formatMillisPrecise(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val tenths = (safe % 1000L) / 100L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths)
}
