package com.suarastudio.tts.audio

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Immutable
data class SavedAudioFile(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val dateAddedMillis: Long,
    val durationMs: Long
)

object MediaStoreSaver {

    private const val FILE_PREFIX = "gemini_voice_studio_"

    fun extractFolderLabel(treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val cleaned = docId.substringAfter(':', docId).trim('/')
            cleaned.ifEmpty { "Folder Terpilih" }
        }.getOrDefault("Folder Terpilih")
    }

    suspend fun saveWavToDirectory(
        context: Context,
        rawOrWavBytes: ByteArray,
        mimeType: String,
        targetDirectory: String,
        treeUriString: String = "",
        folderLabel: String = "Downloads/GeminiVoiceStudio",
        sampleRateOverride: Int = WavAudioUtils.DEFAULT_SAMPLE_RATE,
        onProgress: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0)
            val detectedRate = WavAudioUtils.parseSampleRate(mimeType)
            val sampleRate = if (detectedRate > 0) detectedRate else sampleRateOverride
            val wavBytes = WavAudioUtils.ensureWavBytes(rawOrWavBytes, sampleRate)
            val fileName = "${FILE_PREFIX}${System.currentTimeMillis()}.wav"

            if (treeUriString.isNotBlank()) {
                val resolver = context.contentResolver
                val treeUri = Uri.parse(treeUriString)
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                val createdUri = DocumentsContract.createDocument(
                    resolver,
                    parentDocUri,
                    "audio/wav",
                    fileName
                ) ?: error("Gagal membuat file di folder terpilih. Pilih ulang folder penyimpanan.")

                try {
                    resolver.openOutputStream(createdUri)?.use { out ->
                        writeWithProgress(out, wavBytes, onProgress)
                    } ?: error("Gagal membuka stream penulisan folder terpilih.")
                } catch (e: Exception) {
                    runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
                    throw e
                }
                onProgress(100)
                return@runCatching "${folderLabel.trimEnd('/')}/$fileName"
            }

            val normalizedDir = targetDirectory.trim().trim('/').ifEmpty {
                "${Environment.DIRECTORY_DOWNLOADS}/GeminiVoiceStudio"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val isMusic = normalizedDir.startsWith(Environment.DIRECTORY_MUSIC, ignoreCase = true)
                val collectionUri = if (isMusic) {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(collectionUri, values)
                    ?: error("Gagal membuat entri MediaStore di $normalizedDir.")

                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        writeWithProgress(out, wavBytes, onProgress)
                    } ?: error("Gagal membuka stream penulisan file.")

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val root = Environment.getExternalStorageDirectory()
                val outDir = File(root, normalizedDir)
                if (!outDir.exists()) outDir.mkdirs()
                val outFile = File(outDir, fileName)
                FileOutputStream(outFile).use { out ->
                    writeWithProgress(out, wavBytes, onProgress)
                }
            }
            onProgress(100)
            "$normalizedDir/$fileName"
        }
    }

    private fun writeWithProgress(
        out: java.io.OutputStream,
        data: ByteArray,
        onProgress: (Int) -> Unit
    ) {
        val chunkSize = 8192
        var offset = 0
        val total = data.size.coerceAtLeast(1)
        while (offset < data.size) {
            val count = minOf(chunkSize, data.size - offset)
            out.write(data, offset, count)
            offset += count
            val pct = ((offset * 100L) / total).toInt().coerceIn(1, 99)
            onProgress(pct)
        }
        out.flush()
    }

    suspend fun listSavedWavFiles(
        context: Context,
        treeUriString: String = "",
        folderLabel: String = "Folder Terpilih"
    ): List<SavedAudioFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SavedAudioFile>()

        // 1. Scan user-selected SAF File Manager folder if configured
        if (treeUriString.isNotBlank()) {
            runCatching {
                val resolver = context.contentResolver
                val treeUri = Uri.parse(treeUriString)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".wav", ignoreCase = true)) continue
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val modMillis = if (modCol >= 0) cursor.getLong(modCol) else System.currentTimeMillis()
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val pcmBytes = (size - 44L).coerceAtLeast(0L)
                        val durationMs = (pcmBytes * 1000L) / (WavAudioUtils.DEFAULT_SAMPLE_RATE * 2L)
                        result.add(
                            SavedAudioFile(
                                uri = docUri,
                                displayName = name,
                                relativePath = folderLabel,
                                sizeBytes = size,
                                dateAddedMillis = modMillis,
                                durationMs = durationMs
                            )
                        )
                    }
                }
            }
        }

        // 2. Also scan MediaStore Downloads/Music fallback entries
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uris = listOf(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            )
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED
            )
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("${FILE_PREFIX}%.wav")
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            for (collectionUri in uris) {
                runCatching {
                    resolver.query(
                        collectionUri,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: continue
                            val relPath = cursor.getString(pathCol) ?: Environment.DIRECTORY_DOWNLOADS
                            val size = cursor.getLong(sizeCol)
                            val dateSec = cursor.getLong(dateCol)
                            val uri = ContentUris.withAppendedId(collectionUri, id)
                            val pcmBytes = (size - 44L).coerceAtLeast(0L)
                            val durationMs = (pcmBytes * 1000L) / (WavAudioUtils.DEFAULT_SAMPLE_RATE * 2L)
                            result.add(
                                SavedAudioFile(
                                    uri = uri,
                                    displayName = name,
                                    relativePath = relPath,
                                    sizeBytes = size,
                                    dateAddedMillis = dateSec * 1000L,
                                    durationMs = durationMs
                                )
                            )
                        }
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            downloadsDir.listFiles { file ->
                file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(".wav")
            }?.sortedByDescending { it.lastModified() }?.forEach { file ->
                val size = file.length()
                val pcmBytes = (size - 44L).coerceAtLeast(0L)
                val durationMs = (pcmBytes * 1000L) / (WavAudioUtils.DEFAULT_SAMPLE_RATE * 2L)
                result.add(
                    SavedAudioFile(
                        uri = Uri.fromFile(file),
                        displayName = file.name,
                        relativePath = Environment.DIRECTORY_DOWNLOADS,
                        sizeBytes = size,
                        dateAddedMillis = file.lastModified(),
                        durationMs = durationMs
                    )
                )
            }
        }
        result.distinctBy { it.displayName }.sortedByDescending { it.dateAddedMillis }
    }

    suspend fun readAudioBytes(context: Context, uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            if (uri.scheme == "file") {
                File(requireNotNull(uri.path)).readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Tidak dapat membaca file audio.")
            }
        }
    }

    suspend fun deleteSavedFile(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (uri.scheme == "file") {
                val deleted = File(requireNotNull(uri.path)).delete()
                if (!deleted) error("Gagal menghapus file dari penyimpanan.")
            } else if (DocumentsContract.isDocumentUri(context, uri)) {
                val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                if (!deleted) error("Gagal menghapus file dari folder terpilih.")
            } else {
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows <= 0) error("Gagal menghapus file dari MediaStore.")
            }
        }
    }
}
