package com.suarastudio.tts.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.suarastudio.tts.data.UserSettings
import com.suarastudio.tts.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    currentSettings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (
        apiKey: String,
        themeMode: ThemeMode,
        useDynamicMonet: Boolean,
        modelId: String,
        apiBaseUrl: String,
        apiVersion: String,
        sampleRateHz: Int
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var apiKey by remember(currentSettings.apiKey) { mutableStateOf(currentSettings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var themeMode by remember(currentSettings.themeMode) { mutableStateOf(currentSettings.themeMode) }
    var useMonet by remember(currentSettings.useDynamicMonet) { mutableStateOf(currentSettings.useDynamicMonet) }
    var modelId by remember(currentSettings.modelId) { mutableStateOf(currentSettings.modelId) }
    var apiBaseUrl by remember(currentSettings.apiBaseUrl) { mutableStateOf(currentSettings.apiBaseUrl) }
    var apiVersion by remember(currentSettings.apiVersion) { mutableStateOf(currentSettings.apiVersion) }
    var sampleRateHz by remember(currentSettings.sampleRateHz) { mutableIntStateOf(currentSettings.sampleRateHz) }

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

            // Pengaturan API TTS
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIza...") },
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

            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model API TTS") },
                singleLine = true,
                supportingText = {
                    Text("Contoh: gemini-2.5-flash atau gemini-2.5-flash-preview-tts")
                },
                modifier = Modifier.fillMaxWidth()
            )

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
                            sampleRateHz
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
