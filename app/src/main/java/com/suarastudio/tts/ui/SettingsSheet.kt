package com.suarastudio.tts.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.suarastudio.tts.data.UserSettings
import com.suarastudio.tts.ui.theme.ThemeMode
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    currentSettings: UserSettings,
    detectedModels: List<String>,
    isDetectingModels: Boolean,
    modelDetectionNote: String?,
    onDetectModels: (
        apiKey: String,
        apiBaseUrl: String,
        apiVersion: String,
        onRecommended: (String) -> Unit
    ) -> Unit,
    onResetDailyQuota: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        apiKey: String,
        themeMode: ThemeMode,
        useDynamicMonet: Boolean,
        modelId: String,
        apiBaseUrl: String,
        apiVersion: String,
        sampleRateHz: Int,
        dailyRequestLimit: Int,
        dailyTokenLimit: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var apiKey by remember(currentSettings.apiKey) { mutableStateOf(currentSettings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var themeMode by remember(currentSettings.themeMode) { mutableStateOf(currentSettings.themeMode) }
    var useMonet by remember(currentSettings.useDynamicMonet) { mutableStateOf(currentSettings.useDynamicMonet) }
    var modelId by remember(currentSettings.modelId) { mutableStateOf(currentSettings.modelId) }
    var apiBaseUrl by remember(currentSettings.apiBaseUrl) { mutableStateOf(currentSettings.apiBaseUrl) }
    var apiVersion by remember(currentSettings.apiVersion) { mutableStateOf(currentSettings.apiVersion) }
    var sampleRateHz by remember(currentSettings.sampleRateHz) { mutableIntStateOf(currentSettings.sampleRateHz) }
    var dailyRequestLimit by remember(currentSettings.dailyRequestLimit) { mutableIntStateOf(currentSettings.dailyRequestLimit) }
    var dailyTokenLimit by remember(currentSettings.dailyTokenLimit) { mutableIntStateOf(currentSettings.dailyTokenLimit) }

    val nf = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }

    // Auto-detect models when Settings opens with a saved key and no detected list yet
    LaunchedEffect(Unit) {
        if (apiKey.isNotBlank() && detectedModels.isEmpty() && !isDetectingModels) {
            onDetectModels(apiKey, apiBaseUrl, apiVersion) { recommended ->
                modelId = recommended
            }
        }
    }

    val displayedModelChoices = remember(detectedModels) {
        if (detectedModels.isNotEmpty()) {
            detectedModels
        } else {
            listOf(
                "gemini-3.8-flash-lite-tts",
                "gemini-3.8-flash-tts",
                "gemini-2.5-flash-preview-tts"
            )
        }
    }

    val reqRemaining = (dailyRequestLimit - currentSettings.requestsToday).coerceAtLeast(0)
    val reqProgress = (currentSettings.requestsToday.toFloat() / dailyRequestLimit.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val tokenRemaining = (dailyTokenLimit - currentSettings.totalTokensToday).coerceAtLeast(0)
    val tokenProgress = (currentSettings.totalTokensToday.toFloat() / dailyTokenLimit.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Pengaturan Aplikasi",
                style = MaterialTheme.typography.titleLarge
            )

            // Informasi API TTS
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Informasi API TTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Endpoint: ${apiBaseUrl.trimEnd('/')}/$apiVersion/models/${modelId}:generateContent",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Output: AUDIO (PCM 16-bit Mono · $sampleRateHz Hz / WAV)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (apiKey.isBlank()) "Status Key: Belum diisi" else "Status Key: Tersimpan di DataStore",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Keterangan Kuota & Jatah Kuota TTS Hari Ini
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DataUsage,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Kuota & Jatah TTS Hari Ini",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = onResetDailyQuota) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }

                    // Request quota usage & allowance
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pemakaian Request: ${currentSettings.requestsToday} / $dailyRequestLimit req",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Sisa: $reqRemaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { reqProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Token quota usage & allowance
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pemakaian Token: ${nf.format(currentSettings.totalTokensToday)} / ${nf.format(dailyTokenLimit)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Sisa: ${nf.format(tokenRemaining)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { tokenProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Rincian: Input ${nf.format(currentSettings.promptTokensToday)} · Audio ${nf.format(currentSettings.audioTokensToday)} · Terakhir ${nf.format(currentSettings.lastTokensUsed)} token",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (currentSettings.lastQuotaAlert.isNotBlank()) {
                        Text(
                            text = currentSettings.lastQuotaAlert,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dailyRequestLimit.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { dailyRequestLimit = it.coerceAtLeast(1) }
                            },
                            label = { Text("Jatah Req / Hari") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dailyTokenLimit.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { dailyTokenLimit = it.coerceAtLeast(1000) }
                            },
                            label = { Text("Jatah Token / Hari") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                dailyRequestLimit = 20
                                dailyTokenLimit = 40_000
                            },
                            label = { Text("Free (20 req)") }
                        )
                        SuggestionChip(
                            onClick = {
                                dailyRequestLimit = 50
                                dailyTokenLimit = 100_000
                            },
                            label = { Text("Standar (50 req)") }
                        )
                        SuggestionChip(
                            onClick = {
                                dailyRequestLimit = 250
                                dailyTokenLimit = 500_000
                            },
                            label = { Text("Tier 1 (250 req)") }
                        )
                    }

                    Text(
                        text = "Catatan: API Gemini mengirim usageMetadata per request (tidak mengirim sisa saldo server secara otomatis). Cek kuota resmi Google AI Studio di bawah:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/rate-limit")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cek Kuota Resmi di Google AI Studio")
                    }
                }
            }

            // Pengaturan API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AQ... atau AIza...") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Tampilkan atau sembunyikan API Key"
                        )
                    }
                },
                supportingText = {
                    Text("Disimpan lokal secara aman di perangkat via Jetpack DataStore.")
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Deteksi Model TTS mengikuti API Key yang diinput
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onDetectModels(apiKey, apiBaseUrl, apiVersion) { recommended ->
                            modelId = recommended
                        }
                    },
                    enabled = !isDetectingModels && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDetectingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mendeteksi Model dari API Key...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deteksi Model TTS Sesuai API Key")
                    }
                }

                if (!modelDetectionNote.isNullOrBlank()) {
                    Text(
                        text = modelDetectionNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model API TTS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (detectedModels.isNotEmpty()) {
                        "Model Terdeteksi untuk API Key ini:"
                    } else {
                        "Preset Model TTS:"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    displayedModelChoices.forEach { preset ->
                        FilterChip(
                            selected = modelId.equals(preset, ignoreCase = true),
                            onClick = { modelId = preset },
                            label = { Text(preset, maxLines = 1) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = { apiBaseUrl = it },
                label = { Text("Base URL API TTS") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = apiVersion,
                    onValueChange = { apiVersion = it },
                    label = { Text("Versi API") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = sampleRateHz.toString(),
                    onValueChange = { input ->
                        input.toIntOrNull()?.let { sampleRateHz = it }
                    },
                    label = { Text("Sample Rate (Hz)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Mode Tema",
                    style = MaterialTheme.typography.labelLarge
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to "Sistem",
                        ThemeMode.LIGHT to "Terang",
                        ThemeMode.DARK to "Gelap"
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { themeMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Warna Dinamis Monet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Sesuaikan palet Material 3 dengan wallpaper Android 15",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useMonet,
                        onCheckedChange = { useMonet = it }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        onSave(
                            apiKey,
                            themeMode,
                            useMonet,
                            modelId,
                            apiBaseUrl,
                            apiVersion,
                            sampleRateHz,
                            dailyRequestLimit,
                            dailyTokenLimit
                        )
                    }
                ) {
                    Text("Simpan")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
