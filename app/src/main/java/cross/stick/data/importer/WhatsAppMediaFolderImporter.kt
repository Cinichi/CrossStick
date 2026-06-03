package cross.stick.data.importer

import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

class WhatsAppMediaFolderImporter {

    private val mediaPaths = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Backup Excluded Stickers",
        "WhatsApp/Media/WhatsApp Stickers"
    )

    fun importPacks(): List<UniversalStickerPack> {
        val packs = mutableListOf<UniversalStickerPack>()
        val externalStorage = Environment.getExternalStorageDirectory()

        for (relativePath in mediaPaths) {
            val dir = File(externalStorage, relativePath)
            if (dir.exists() && dir.canRead()) {
                val pack = createPackFromDirectory(dir, relativePath)
                if (pack != null && pack.stickers.isNotEmpty()) {
                    packs.add(pack)
                }
            }
        }

        return packs
    }

    private fun createPackFromDirectory(dir: File, path: String): UniversalStickerPack? {
        val files = dir.listFiles()?.filter { 
            it.isFile && it.extension.equals("webp", ignoreCase = true) 
        } ?: return null

        if (files.isEmpty()) return null

        // Try to find STK- prefixed files (WhatsApp saved stickers)
        val stkFiles = files.filter { it.name.startsWith("STK-") }
        val sourceFiles = if (stkFiles.isNotEmpty()) stkFiles else files

        // Group by date proximity (1 hour windows)
        val sorted = sourceFiles.sortedBy { it.lastModified() }
        val groups = mutableListOf<MutableList<File>>()
        var current = mutableListOf<File>()
        var lastTime = 0L

        for (file in sorted) {
            if (current.isEmpty() || kotlin.math.abs(file.lastModified() - lastTime) < 3600_000) {
                current.add(file)
            } else {
                if (current.isNotEmpty()) groups.add(current)
                current = mutableListOf(file)
            }
            lastTime = file.lastModified()
        }
        if (current.isNotEmpty()) groups.add(current)

        if (groups.size == 1) {
            // Single group - likely one pack
            val stickers = groups[0].mapIndexed { index, file ->
                createSticker(file, index)
            }
            return UniversalStickerPack(
                id = "wa-media-${path.hashCode()}",
                title = "WhatsApp Stickers",
                sourcePackage = "com.whatsapp",
                sourceAuthority = null,
                sourceLayer = SourceLayer.WHATSAPP_MEDIA_FOLDER,
                confidence = Confidence.LOW,
                format = StickerFormat.STATIC,
                stickers = stickers
            )
        }

        return null
    }

    private fun createSticker(file: File, index: Int): UniversalSticker {
        val format = StickerFileTypeDetector.detectFormat(file)
        return UniversalSticker(
            id = "wa-media-$index",
            sourcePath = file.absolutePath,
            sourceUri = null,
            localFile = file,
            originalFileName = file.name,
            emojiList = listOf("😀"),
            index = index,
            mimeType = StickerFileTypeDetector.getMimeType(format)
        )
    }
}
