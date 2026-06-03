package cross.stick.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cross.stick.conversion.ConversionEngine
import cross.stick.data.local.PreferencesManager
import cross.stick.data.model.TelegramStickerSet
import cross.stick.data.repository.StickerRepository
import cross.stick.whatsapp.WhatsAppIntentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val MAX_WHATSAPP_STICKERS = 30
private const val MIN_WHATSAPP_STICKERS = 3
private const val TELEGRAM_CREATE_STICKER_PACK_ACTION = "org.telegram.messenger.CREATE_STICKER_PACK"
private const val TELEGRAM_STICKER_EMOJIS_EXTRA = "STICKER_EMOJIS"
private const val TELEGRAM_IMPORTER_EXTRA = "IMPORTER"

data class SavedPack(
    val id: String,
    val name: String,
    val stickerCount: Int,
    val path: File
)

data class PreviewSticker(
    val file: File,
    val emoji: String = "😀"
)

sealed class ImportPhase {
    object Idle : ImportPhase()
    object Fetching : ImportPhase()
    data class Downloading(val current: Int, val total: Int) : ImportPhase()
    object PreviewReady : ImportPhase()
    data class Converting(val current: Int, val total: Int) : ImportPhase()
    object Done : ImportPhase()
    data class Failed(val error: String) : ImportPhase()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = StickerRepository(application)

    val botToken: StateFlow<String> = prefs.botToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val authorName: StateFlow<String> = prefs.authorName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val onboardingComplete: StateFlow<Boolean> = prefs.onboardingComplete.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _phase = MutableStateFlow<ImportPhase>(ImportPhase.Idle)
    val phase: StateFlow<ImportPhase> = _phase.asStateFlow()

    private val _currentPackId = MutableStateFlow<String?>(null)
    val currentPackId: StateFlow<String?> = _currentPackId.asStateFlow()

    private val _convertedPackId = MutableStateFlow<String?>(null)
    val convertedPackId: StateFlow<String?> = _convertedPackId.asStateFlow()

    private val _previewStickers = MutableStateFlow<List<PreviewSticker>>(emptyList())
    val previewStickers: StateFlow<List<PreviewSticker>> = _previewStickers.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<List<File>>(emptyList())
    val downloadedFilePaths: List<File> get() = _downloadedFiles.value

    private val _savedPacks = MutableStateFlow<List<SavedPack>>(emptyList())
    val savedPacks: StateFlow<List<SavedPack>> = _savedPacks.asStateFlow()

    init {
        viewModelScope.launch {
            onboardingComplete.collect { _isReady.value = true }
        }
        loadSavedPacks()
    }

    private fun loadSavedPacks() {
        val context = getApplication<Application>()
        val packsDir = File(context.filesDir, "stickers/converted")
        if (!packsDir.exists()) {
            _savedPacks.value = emptyList()
            return
        }
        _savedPacks.value = packsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { packDir ->
                val stickerCount = packDir.listFiles { f -> f.extension.equals("webp", ignoreCase = true) }?.size ?: 0
                if (stickerCount == 0) null else SavedPack(id = packDir.name, name = packDir.name, stickerCount = stickerCount, path = packDir)
            } ?: emptyList()
    }

    fun completeOnboarding(token: String, author: String) {
        viewModelScope.launch {
            prefs.saveBotToken(token)
            prefs.saveAuthorName(author)
            prefs.completeOnboarding()
        }
    }

    fun updateSettings(token: String, author: String) {
        viewModelScope.launch {
            prefs.saveBotToken(token)
            prefs.saveAuthorName(author)
        }
    }

    fun fetchStickerSet(link: String) {
        viewModelScope.launch {
            _phase.value = ImportPhase.Fetching
            _previewStickers.value = emptyList()
            _downloadedFiles.value = emptyList()
            _convertedPackId.value = null

            val packName = repository.extractPackName(link)
            val result = repository.fetchStickerSet(packName)
            result.fold(
                onSuccess = { stickerSet ->
                    val packId = stickerSet.name.sanitizePackId()
                    _currentPackId.value = packId
                    downloadForPreview(stickerSet, packId)
                },
                onFailure = { e ->
                    _phase.value = ImportPhase.Failed(e.message ?: "Could not fetch stickers")
                }
            )
        }
    }

    private suspend fun downloadForPreview(stickerSet: TelegramStickerSet, packId: String) {
        val staticStickers = stickerSet.stickers
            .filterNot { it.is_animated || it.is_video }
            .take(MAX_WHATSAPP_STICKERS)

        if (staticStickers.size < MIN_WHATSAPP_STICKERS) {
            _phase.value = ImportPhase.Failed("This pack has fewer than 3 static stickers. WhatsApp requires at least 3 static stickers.")
            return
        }

        val files = mutableListOf<PreviewSticker>()
        val rawDir = File(getApplication<Application>().filesDir, "stickers/raw/$packId")
        if (rawDir.exists()) rawDir.deleteRecursively()
        rawDir.mkdirs()

        staticStickers.forEachIndexed { index, sticker ->
            _phase.value = ImportPhase.Downloading(index + 1, staticStickers.size)
            repository.downloadSticker(sticker.file_id, packId, index).fold(
                onSuccess = { file ->
                    files.add(PreviewSticker(file = file, emoji = sticker.emoji?.takeIf { it.isNotBlank() } ?: "😀"))
                },
                onFailure = { e ->
                    Log.e("CrossStick", "Failed sticker $index: ${e.message}")
                }
            )
        }

        _downloadedFiles.value = files.map { it.file }
        _previewStickers.value = files

        _phase.value = if (files.size >= MIN_WHATSAPP_STICKERS) {
            ImportPhase.PreviewReady
        } else {
            ImportPhase.Failed("Only ${files.size} stickers could be downloaded. WhatsApp needs at least 3 stickers.")
        }
    }

    fun removePreviewSticker(index: Int) {
        val current = _previewStickers.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        _previewStickers.value = current
    }

    fun addPreviewUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val packId = _currentPackId.value ?: "manual_${System.currentTimeMillis()}"
            _currentPackId.value = packId
            val copied = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { index, uri -> copyUriToRawPreview(uri, packId, index) }
            }
            if (copied.isNotEmpty()) {
                _previewStickers.value = (_previewStickers.value + copied).take(MAX_WHATSAPP_STICKERS)
                _phase.value = ImportPhase.PreviewReady
            }
        }
    }

    private fun copyUriToRawPreview(uri: Uri, packId: String, index: Int): PreviewSticker? {
        val context = getApplication<Application>()
        return try {
            val rawDir = File(context.filesDir, "stickers/raw/$packId")
            if (!rawDir.exists()) rawDir.mkdirs()
            val outFile = File(rawDir, "added_${System.currentTimeMillis()}_$index.webp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return null
            PreviewSticker(outFile, "😀")
        } catch (e: Exception) {
            Log.e("CrossStick", "Could not copy selected sticker", e)
            null
        }
    }

    fun convertPreviewToWhatsApp() {
        viewModelScope.launch {
            val packId = _currentPackId.value ?: "pack_${System.currentTimeMillis()}"
            val selected = _previewStickers.value.take(MAX_WHATSAPP_STICKERS)
            if (selected.size < MIN_WHATSAPP_STICKERS) {
                _phase.value = ImportPhase.Failed("Select at least 3 stickers before converting.")
                return@launch
            }

            val outputDir = File(getApplication<Application>().filesDir, "stickers/converted/$packId")
            withContext(Dispatchers.IO) {
                if (outputDir.exists()) outputDir.deleteRecursively()
                outputDir.mkdirs()
            }

            val converted = mutableListOf<File>()
            selected.forEachIndexed { index, previewSticker ->
                _phase.value = ImportPhase.Converting(index + 1, selected.size)
                val result = withContext(Dispatchers.Default) {
                    ConversionEngine.convertToWhatsAppStatic(
                        inputFile = previewSticker.file,
                        outputDir = outputDir,
                        outputName = "sticker_${index.toString().padStart(2, '0')}.webp"
                    )
                }
                result.fold(
                    onSuccess = { converted.add(it) },
                    onFailure = { e -> Log.e("CrossStick", "Conversion failed for sticker $index: ${e.message}") }
                )
            }

            if (converted.size < MIN_WHATSAPP_STICKERS) {
                _phase.value = ImportPhase.Failed("Only ${converted.size} stickers could be converted. WhatsApp needs at least 3 valid stickers.")
                return@launch
            }

            withContext(Dispatchers.Default) {
                ConversionEngine.createTrayFromFile(converted.first(), outputDir)
            }

            _phase.value = ImportPhase.Done
            _convertedPackId.value = packId
            _previewStickers.value = emptyList()
            loadSavedPacks()
            addToWhatsApp(packId)
        }
    }

    fun addToWhatsApp(packId: String) {
        val context = getApplication<Application>()
        WhatsAppIntentHelper.addStickerPackToWhatsApp(
            context = context,
            packId = packId,
            packName = packId,
            authority = "cross.stick.stickercontentprovider"
        )
    }

    fun resetPhase() {
        _phase.value = ImportPhase.Idle
    }

    fun importToTelegram(uris: List<Uri>, emojis: List<String>) {
        val context = getApplication<Application>()
        if (uris.isEmpty()) {
            Toast.makeText(context, "Select at least one WebP sticker", Toast.LENGTH_SHORT).show()
            return
        }

        val normalizedEmojis = if (emojis.size == uris.size) emojis else List(uris.size) { "😀" }
        val baseIntent = Intent(TELEGRAM_CREATE_STICKER_PACK_ACTION).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putStringArrayListExtra(TELEGRAM_STICKER_EMOJIS_EXTRA, ArrayList(normalizedEmojis))
            putExtra(TELEGRAM_IMPORTER_EXTRA, context.packageName)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            val handlers = context.packageManager.queryIntentActivities(
                baseIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            handlers.forEach { resolveInfo ->
                uris.forEach { uri ->
                    context.grantUriPermission(
                        resolveInfo.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            val targetPackage = handlers
                .map { it.activityInfo.packageName }
                .firstOrNull { it == "org.telegram.messenger" }
                ?: handlers.firstOrNull()?.activityInfo?.packageName

            val launchIntent = Intent(baseIntent).apply {
                targetPackage?.let { setPackage(it) }
            }

            context.startActivity(launchIntent)
            Toast.makeText(context, "Opening Telegram...", Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Log.e("CrossStick", "Telegram import activity not found", e)
            Toast.makeText(context, "Telegram is not installed or does not support sticker import.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("CrossStick", "Telegram import failed", e)
            Toast.makeText(context, "Failed to open Telegram: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun String.sanitizePackId(): String {
        return replace(Regex("[^A-Za-z0-9_. -]"), "_")
            .trim()
            .ifBlank { "pack_${System.currentTimeMillis()}" }
            .take(120)
    }
}
