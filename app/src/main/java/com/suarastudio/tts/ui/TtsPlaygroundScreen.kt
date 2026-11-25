package com.suarastudio.tts.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suarastudio.tts.data.GeminiVoice
import com.suarastudio.tts.ui.theme.ThemeMode
import java.text.NumberFormat
import java.util.Locale

private data class StylePreset(
    val title: String,
    val instruction: String,
    val sampleText: String
)

private val AI_STUDIO_STYLE_PRESETS = listOf(
    StylePreset(
        title = "Default (Tanpa Gaya Khusus)",
        instruction = "",
        sampleText = "Halo, ini adalah suara standar tanpa instruksi gaya tambahan."
    ),
    StylePreset(
        title = "The Everyday Assistant",
        instruction = "Speak in a helpful, warm, natural, and conversational tone like an everyday personal assistant",
        sampleText = "Selamat pagi! Jadwal rapat Anda dimulai pukul sembilan, dan cuaca hari ini cerah."
    ),
    StylePreset(
        title = "The Guarded NPC",
        instruction = "Speak cautiously and suspiciously in a low, guarded tone like a wary video game NPC",
        sampleText = "Berhenti di situ. Siapa yang mengirimmu ke gerbang ini malam-malam?"
    ),
    StylePreset(
        title = "The Energetic Co-Host",
        instruction = "Sound lively, enthusiastic, witty, and engaging like an energetic podcast co-host",
        sampleText = "Wah, seru banget topik hari ini! Yuk langsung kita bahas rahasianya bareng-bareng."
    ),
    StylePreset(
        title = "The Master Storyteller",
        instruction = "Narrate with rich dramatic pacing, vivid warmth, and captivating storytelling expression",
        sampleText = "Di balik kabut pegunungan yang sunyi, sebuah lentera tua mulai menyala perlahan."
    ),
    StylePreset(
        title = "The Ads Voiceover",
        instruction = "Deliver with high energy, crisp articulation, and persuasive commercial voiceover punch",
        sampleText = "Rasakan suara jernih berkualitas studio hanya dalam satu sentuhan, sekarang juga!"
    ),
    StylePreset(
        title = "The Training Guide",
        instruction = "Speak clearly, calmly, and methodically like a supportive step-by-step training guide",
        sampleText = "Langkah pertama, tekan tombol Pengaturan di pojok kanan atas untuk memasukkan kunci API Anda."
    ),
    StylePreset(
        title = "The Game Show Host",
        instruction = "Project excitement, bright charisma, and upbeat theatrical flair like a game show host",
        sampleText = "Luar biasa! Jawaban Anda tepat sekali dan membawa pulang hadiah utama malam ini!"
    ),
    StylePreset(
        title = "The Patient Teacher",
        instruction = "Explain patiently, gently, and encouragingly at a comfortable, clear pace like a teacher",
        sampleText = "Tidak apa-apa kalau belum langsung paham. Mari kita pelajari bagian ini pelan-pelan bersama."
    ),
    StylePreset(
        title = "Natural & Percakapan",
        instruction = "Speak naturally and conversationally, as if talking to a friend",
        sampleText = "Eh, nanti sore jadi kan kita ketemu buat bahas ide proyek barunya?"
    ),
    StylePreset(
        title = "Ceria & Antusias",
        instruction = "Read in a cheerful, upbeat, and enthusiastic tone",
        sampleText = "Yeay! Akhirnya proyek audio kita berhasil jalan dengan mulus!"
    ),
    StylePreset(
        title = "Penyiar Berita Formal",
        instruction = "Read with the clear, composed authority of a professional news anchor",
        sampleText = "Pemirsa, pembaruan teknologi kecerdasan buatan hari ini menghadirkan kualitas suara makin alami."
    ),
    StylePreset(
        title = "Narator Dokumenter",
        instruction = "Narrate in a calm, rich, cinematic documentary voice with measured pacing",
        sampleText = "Ribuan kilometer di bawah permukaan laut, kehidupan terus beradaptasi dalam gelap."
    ),
    StylePreset(
        title = "Berbisik Pelan / ASMR",
        instruction = "Whisper softly and intimately, slow and gentle",
        sampleText = "Dengarkan perlahan... suasana malam ini begitu tenang dan damai."
    ),
    StylePreset(
        title = "Dramatis & Emosional",
        instruction = "Deliver with deep dramatic tension, emotion, and expressive pauses",
        sampleText = "Waktu kita tidak banyak lagi... keputusan ini akan mengubah segalanya."
    ),
    StylePreset(
        title = "Pendongeng Anak",
        instruction = "Read warmly and playfully like an engaging storyteller for children",
        sampleText = "Suatu hari, seekor kelinci kecil menemukan peta harta karun di taman bunga."
    ),
    StylePreset(
        title = "Customer Service Ramah",
        instruction = "Speak politely, warmly, and clearly like a helpful customer support agent",
        sampleText = "Terima kasih telah menghubungi kami. Ada yang bisa saya bantu hari ini?"
    ),
    StylePreset(
        title = "Meditasi & Relaksasi",
        instruction = "Speak very slowly, soothingly, and peacefully for guided meditation",
        sampleText = "Tarik napas dalam secara perlahan... lalu hembuskan dengan tenang."
    ),
    StylePreset(
        title = "Serius & Tegas",
        instruction = "Speak firmly, seriously, and concisely without unnecessary warmth",
        sampleText = "Pastikan seluruh prosedur keamanan sudah diperiksa sebelum sistem dijalankan."
    ),
    StylePreset(
        title = "Trailer Film Epik",
        instruction = "Deliver in a deep, resonant, dramatic movie trailer voice with impactful pauses",
        sampleText = "Ketika semua harapan memudar... hanya satu suara yang tersisa."
    )
)

private enum class VoicePickerTarget {
    SINGLE,
    SPEAKER_1,
    SPEAKER_2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsPlaygroundScreen(viewModel: TtsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var voicePickerTarget by remember { mutableStateOf<VoicePickerTarget?>(null) }
    var isStyleSheetOpen by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            viewModel.updateDownloadFolder(uri)
        }
    }

    // Consume leftover vertical scroll inside the script box so dragging inside the box moves ONLY text
    val scriptScrollIsolator = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(0f, available.y)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = Velocity(0f, available.y)
        }
    }

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
                    val (indicatorColor, statusLabel) = when (uiState.isApiConnected) {
                        true -> Color(0xFF16A34A) to "Connected"
                        false -> Color(0xFFDC2626) to "Disconnected"
                        null -> MaterialTheme.colorScheme.outline to "Checking"
                    }
                    Surface(
                        color = indicatorColor.copy(alpha = 0.12f),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                            )
                            Text(
                                text = statusLabel,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = indicatorColor,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openLogs) {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = "Log API dan aktivitas pemakaian TTS"
                        )
                    }
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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

                    // Compact Voice Selector Button (opens clean modal list with Play preview)
                    if (!uiState.isMultiSpeaker) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { voicePickerTarget = VoicePickerTarget.SINGLE }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "Suara Gemini",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${uiState.singleVoice.voiceName} — ${uiState.singleVoice.characterNote}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Pilih daftar suara"
                                )
                            }
                        }
                    } else {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Konfigurasi 2 Pembicara",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                SpeakerPickerRow(
                                    label = "Pembicara 1",
                                    speakerName = uiState.speaker1Name,
                                    selectedVoice = uiState.speaker1Voice,
                                    onNameChange = viewModel::updateSpeaker1Name,
                                    onOpenVoiceList = { voicePickerTarget = VoicePickerTarget.SPEAKER_1 }
                                )
                                SpeakerPickerRow(
                                    label = "Pembicara 2",
                                    speakerName = uiState.speaker2Name,
                                    selectedVoice = uiState.speaker2Voice,
                                    onNameChange = viewModel::updateSpeaker2Name,
                                    onOpenVoiceList = { voicePickerTarget = VoicePickerTarget.SPEAKER_2 }
                                )
                            }
                        }
                    }

                    // Compact Style / Director's Notes with AI Studio Preset Picker Button
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.styleNotes,
                            onValueChange = viewModel::updateStyleNotes,
                            label = { Text("Instruksi Gaya / Emosi (Opsional)") },
                            placeholder = { Text("Tulis manual atau pilih dari daftar gaya AI Studio") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { isStyleSheetOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pilih Daftar Instruksi Gaya (AI Studio)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    }

                    // Folder Picker via Android File Manager (SAF OpenDocumentTree)
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Folder Penyimpanan Audio",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = settings.downloadFolderLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) }
                            ) {
                                Text("Pilih Folder", maxLines = 1)
                            }
                        }
                    }

                    // Fixed-height isolated scrollable script text box (only inner text scrolls)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .nestedScroll(scriptScrollIsolator)
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

                    // Compact Kuota & Jatah TTS Summary Card (tap to configure in Settings)
                    val nf = remember { NumberFormat.getIntegerInstance(Locale("id", "ID")) }
                    val reqRemaining = (settings.dailyRequestLimit - settings.requestsToday).coerceAtLeast(0)
                    val reqProgress = (settings.requestsToday.toFloat() / settings.dailyRequestLimit.coerceAtLeast(1).toFloat())
                        .coerceIn(0f, 1f)
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openSettings() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                Text(
                                    text = "Atur >",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Terpakai: ${settings.requestsToday} / ${settings.dailyRequestLimit} req · Sisa: $reqRemaining",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${nf.format(settings.totalTokensToday)} token",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { reqProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            val (jenisGemini, tierStatus) = resolveGeminiAccountDisplay(uiState, settings)
                            Text(
                                text = "Jenis Gemini: $jenisGemini · $tierStatus",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (settings.lastQuotaAlert.isNotBlank()) {
                                Text(
                                    text = settings.lastQuotaAlert,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            AppTab.ARTIFACTS -> {
                ResultsTab(
                    savedFiles = uiState.savedFiles,
                    activeSavedUri = uiState.activeSavedUri,
                    playbackState = playbackState,
                    onRefresh = { viewModel.refreshSavedFiles() },
                    onPlayFile = viewModel::playSavedFile,
                    onDeleteFile = viewModel::deleteSavedFile,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppTab.TENTANG -> {
                AboutTab(
                    uiState = uiState,
                    settings = settings,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    // Voice list ModalBottomSheet with Play Preview button
    voicePickerTarget?.let { target ->
        val selectedVoice = when (target) {
            VoicePickerTarget.SINGLE -> uiState.singleVoice
            VoicePickerTarget.SPEAKER_1 -> uiState.speaker1Voice
            VoicePickerTarget.SPEAKER_2 -> uiState.speaker2Voice
        }
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.stopPreview()
                voicePickerTarget = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Pilih Suara Gemini",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                GeminiVoice.entries.forEach { voice ->
                    val isSelected = voice == selectedVoice
                    val previewKey = "voice:${voice.voiceName}"
                    val isThisPreviewing = uiState.previewingKey == previewKey
                    val isLoadingThis = isThisPreviewing && uiState.isPreviewLoading
                    val isPlayingThis = isThisPreviewing && playbackState.isPlaying

                    ListItem(
                        headlineContent = {
                            Text(
                                text = voice.voiceName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        supportingContent = { Text(voice.characterNote) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Terpilih",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.previewVoice(voice) }
                                ) {
                                    when {
                                        isLoadingThis -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        isPlayingThis -> {
                                            Icon(
                                                imageVector = Icons.Rounded.Stop,
                                                contentDescription = "Hentikan preview ${voice.voiceName}",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Preview suara ${voice.voiceName}"
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            when (target) {
                                VoicePickerTarget.SINGLE -> viewModel.selectSingleVoice(voice)
                                VoicePickerTarget.SPEAKER_1 -> viewModel.updateSpeaker1Voice(voice)
                                VoicePickerTarget.SPEAKER_2 -> viewModel.updateSpeaker2Voice(voice)
                            }
                            viewModel.stopPreview()
                            voicePickerTarget = null
                        }
                    )
                }
            }
        }
    }

    // Expanded AI Studio Style / Director's Notes ModalBottomSheet with Play Preview button
    if (isStyleSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.stopPreview()
                isStyleSheetOpen = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Instruksi Gaya AI Studio (${AI_STUDIO_STYLE_PRESETS.size} Pilihan)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                ) {
                    items(AI_STUDIO_STYLE_PRESETS) { preset ->
                        val isSelected = uiState.styleNotes == preset.instruction
                        val previewKey = "style:${preset.title}"
                        val isThisPreviewing = uiState.previewingKey == previewKey
                        val isLoadingThis = isThisPreviewing && uiState.isPreviewLoading
                        val isPlayingThis = isThisPreviewing && playbackState.isPlaying

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = preset.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            supportingContent = if (preset.instruction.isNotEmpty()) {
                                { Text(preset.instruction, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            } else {
                                { Text("Gunakan nada bicara standar model tanpa instruksi tambahan") }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Terpilih",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.previewStyle(
                                                styleTitle = preset.title,
                                                styleInstruction = preset.instruction,
                                                sampleText = preset.sampleText
                                            )
                                        }
                                    ) {
                                        when {
                                            isLoadingThis -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                            isPlayingThis -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.Stop,
                                                    contentDescription = "Hentikan preview ${preset.title}",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "Preview gaya ${preset.title}"
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateStyleNotes(preset.instruction)
                                viewModel.stopPreview()
                                isStyleSheetOpen = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isSettingsOpen) {
        SettingsSheet(
            currentSettings = settings,
            detectedModels = uiState.detectedTtsModels,
            isDetectingModels = uiState.isDetectingModels,
            modelDetectionNote = uiState.modelDetectionNote,
            onDetectModels = { key, base, ver, onRec ->
                viewModel.detectModelsForKey(key, base, ver, onRec)
            },
            onResetDailyQuota = viewModel::resetDailyQuotaUsage,
            onDismiss = viewModel::closeSettings,
            onSave = viewModel::saveSettings
        )
    }

    if (uiState.isLogSheetOpen) {
        ApiLogSheet(
            logs = uiState.apiLogs,
            settings = settings,
            onClearLogs = viewModel::clearLogs,
            onDismiss = viewModel::closeLogs
        )
    }
}

@Composable
private fun SpeakerPickerRow(
    label: String,
    speakerName: String,
    selectedVoice: GeminiVoice,
    onNameChange: (String) -> Unit,
    onOpenVoiceList: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = speakerName,
            onValueChange = onNameChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        OutlinedButton(
            onClick = onOpenVoiceList,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
        ) {
            Text(
                text = selectedVoice.voiceName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Pilih suara $label"
            )
        }
    }
}
