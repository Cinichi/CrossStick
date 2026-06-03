package cross.stick.data.importer

import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

class WhatsAppMediaFolderImporter {

    fun importPacks(): List<UniversalStickerPack> {
        val packs = mutableListOf<UniversalStickerPack>()

        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val external = Environment.getExternalStorageDirectory()
            File(external, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers")
        } else {
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Stickers")
        }

        if (!baseDir.exists() || !baseDir.canRead()) {
            Log.d("WAImporter", "Media folder not accessible")
            return packs
        }

        // Group by folder modified time
        val allFiles = baseDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("webp", ignoreCase = true) }
            .toList()

        if (allFiles.isEmpty()) return packs

        // Simple grouping by creation time proximity (1 hour)
        val sortedFiles = allFiles.sortedBy { it.lastModified() }
        val groups = mutableListOf<MutableList<File>>()
        var currentGroup = mutableListOf<File>()
        var groupTime = 0L

        for (file in sortedFiles) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(file)
                groupTime = file.lastModified()
            } else {
                if (kotlin.math.abs(file.lastModified() - groupTime) < 3600_000) { // 1 hour
                    currentGroup.add(file)
                } else {
                    groups.add(currentGroup)
                    currentGroup = mutableListOf(file)
                }
                groupTime = file.lastModified()
            }
        }
        if (currentGroup.isNotEmpty()) groups.add(currentGroup)

        groups.forEachIndexed { index, files ->
            val stickers = files.mapIndexed { i, file ->
                val format = StickerFileTypeDetector.detectFormat(file)
                val mimeType = StickerFileTypeDetector.getMimeType(format)
                UniversalSticker(
                    id = "wa-media-$index-$i",
                    file = file,
                    originalFileName = file.name,
                    emojiList = listOf("😀"),
                    index = i,
                    width = null, height = null,
                    mimeType = mimeType
                )
            }

            packs.add(UniversalStickerPack(
                id = "wa-media-pack-$index",
                title = "WhatsApp Stickers ${index + 1}",
                sourceAppPackage = "com.whatsapp",
                sourceAuthority = null,
                sourceType = SourceType.WHATSAPP_MEDIA_FOLDER,
                format = StickerFormat.STATIC,
                stickers = stickers
            ))
        }

        return packs
    }
}
