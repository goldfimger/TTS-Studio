package com.suarastudio.tts.ui

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suarastudio.tts.data.GeminiVoice
import com.suarastudio.tts.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TtsPlaygroundScreen(viewModel: TtsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Gemini Voice Studio",
                            style = MaterialTheme.typography.titleLarge
                        )
                        val indicatorColor = when (uiState.isApiConnected) {
                            true -> Color(0xFF16A34A)
                            false -> Color(0xFFDC2626)
                            null -> MaterialTheme.colorScheme.outline
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleQuickTheme(isDark) }) {
                        Icon(
                            imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Ubah mode terang atau gelap"
                        )
                    }
                    IconButton(onClick = viewModel::openSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Pengaturan API TTS"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (uiState.activeTab == AppTab.PROJEK) {
                    AudioPlayerBar(
                        playbackState = playbackState,
                        isSavingWav = uiState.isSavingWav,
                        downloadProgress = uiState.downloadProgress,
                        isDownloadDone = uiState.isDownloadDone,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onStop = viewModel::stopPlayback,
                        onSeek = viewModel::seekTo,
                        onSaveWav = viewModel::saveWavToDownloads
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = uiState.activeTab == AppTab.PROJEK,
                        onClick = { viewModel.selectTab(AppTab.PROJEK) },
                        icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        label = { Text(AppTab.PROJEK.label) }
                    )
                    NavigationBarItem(
                        selected = uiState.activeTab == AppTab.ARTIFACTS,
                        onClick = { viewModel.selectTab(AppTab.ARTIFACTS) },
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                        label = { Text(AppTab.ARTIFACTS.label) }
                    )
                    NavigationBarItem(
                        selected = uiState.activeTab == AppTab.TENTANG,
                        onClick = { viewModel.selectTab(AppTab.TENTANG) },
                        icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        label = { Text(AppTab.TENTANG.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (uiState.activeTab) {
            AppTab.PROJEK -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !uiState.isMultiSpeaker,
                            onClick = { viewModel.setMode(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Single Voice", maxLines = 1)
                        }
                        SegmentedButton(
                            selected = uiState.isMultiSpeaker,
                            onClick = { viewModel.setMode(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Multi-Speaker", maxLines = 1)
                        }
                    }

                    if (!uiState.isMultiSpeaker) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Pilih Suara Gemini",
                                style = MaterialTheme.typography.labelLarge
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                GeminiVoice.entries.forEach { voice ->
                                    FilterChip(
                                        selected = uiState.singleVoice == voice,
                                        onClick = { viewModel.selectSingleVoice(voice) },
                                        label = { Text("${voice.voiceName} (${voice.characterNote})") }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Konfigurasi 2 Pembicara",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                SpeakerRow(
                                    label = "Pembicara 1",
                                    speakerName = uiState.speaker1Name,
                                    selectedVoice = uiState.speaker1Voice,
                                    onNameChange = viewModel::updateSpeaker1Name,
                                    onVoiceChange = viewModel::updateSpeaker1Voice
                                )
                                SpeakerRow(
                                    label = "Pembicara 2",
                                    speakerName = uiState.speaker2Name,
                                    selectedVoice = uiState.speaker2Voice,
                                    onNameChange = viewModel::updateSpeaker2Name,
                                    onVoiceChange = viewModel::updateSpeaker2Voice
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.styleNotes,
                            onValueChange = viewModel::updateStyleNotes,
                            label = { Text("Instruksi Gaya / Emosi (Opsional)") },
                            placeholder = { Text("Contoh: Baca dengan nada ceria dan antusias") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "Baca dengan nada ceria dan antusias",
                                "Berbisik pelan",
                                "Gaya penyiar berita formal"
                            ).forEach { preset ->
                                SuggestionChip(
                                    onClick = { viewModel.updateStyleNotes(preset) },
                                    label = { Text(preset, maxLines = 1) }
                                )
                            }
                        }
                    }

                    // Direktori penyimpanan hasil download
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Direktori Penyimpanan Download",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            OutlinedTextField(
                                value = settings.downloadDirectory,
                                onValueChange = viewModel::updateDownloadDirectory,
                                singleLine = true,
                                label = { Text("Folder Tujuan (Scoped Storage)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "${Environment.DIRECTORY_DOWNLOADS}/GeminiVoiceStudio",
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "${Environment.DIRECTORY_MUSIC}/GeminiVoiceStudio"
                                ).forEach { presetDir ->
                                    SuggestionChip(
                                        onClick = { viewModel.updateDownloadDirectory(presetDir) },
                                        label = { Text(presetDir, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.scriptText,
                        onValueChange = viewModel::updateScriptText,
                        label = {
                            Text(
                                if (uiState.isMultiSpeaker) {
                                    "Naskah Percakapan (ID / EN)"
                                } else {
                                    "Naskah Teks (ID / EN)"
                                }
                            )
                        },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = viewModel::generateAndPlay,
                        enabled = !uiState.isGenerating && uiState.scriptText.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (uiState.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Memproses Audio Gemini...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate & Play")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            AppTab.ARTIFACTS -> {
                ResultsTab(
                    savedFiles = uiState.savedFiles,
                    activeSavedUri = uiState.activeSavedUri,
                    playbackState = playbackState,
                    onRefresh = viewModel::refreshSavedFiles,
                    onPlayFile = viewModel::playSavedFile,
                    onDeleteFile = viewModel::deleteSavedFile,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppTab.TENTANG -> {
                AboutTab(modifier = Modifier.padding(innerPadding))
            }
        }
    }

    if (uiState.isSettingsOpen) {
        SettingsSheet(
            currentSettings = settings,
            onDismiss = viewModel::closeSettings,
            onSave = viewModel::saveSettings
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeakerRow(
    label: String,
    speakerName: String,
    selectedVoice: GeminiVoice,
    onNameChange: (String) -> Unit,
    onVoiceChange: (GeminiVoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = speakerName,
            onValueChange = onNameChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedVoice.voiceName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Suara") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                GeminiVoice.entries.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text("${voice.voiceName} — ${voice.characterNote}") },
                        onClick = {
                            onVoiceChange(voice)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
