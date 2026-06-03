package cross.stick.data.importer

import android.util.Log
import java.io.File
import java.io.FileOutputStream

class WhatsAppInternalStickerImporter {

    private val stickerDir = "/data/data/com.whatsapp/files/Stickers"

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun importPacks(cacheDir: File): List<UniversalStickerPack> {
        if (!isRootAvailable()) return emptyList()

        val sourceDir = File(stickerDir)
        if (!sourceDir.exists() || !sourceDir.canRead()) {
            Log.d("WAInternal", "Sticker dir not accessible")
            return emptyList()
        }

        val authorityGroups = mutableMapOf<String, MutableMap<String, MutableList<File>>>()
        
        sourceDir.listFiles()?.filter { it.isFile }?.forEach { file ->
            if (file.length() == 0L) return@forEach // skip zero-byte placeholders
            val parsed = ProviderPrefixParser.parse(file.name)
            if (parsed.authority != null) {
                val packName = ProviderPrefixParser.inferPackNameFromAsset(parsed.assetName)
                authorityGroups
                    .getOrPut(parsed.authority) { mutableMapOf() }
                    .getOrPut(packName) { mutableListOf() }
                    .add(file)
            }
        }

        val packs = mutableListOf<UniversalStickerPack>()
        
        for ((authority, packGroups) in authorityGroups) {
            for ((packTitle, files) in packGroups) {
                if (files.isEmpty()) continue

                val stickers = files.mapIndexed { index, file ->
                    val format = StickerFileTypeDetector.detectFormat(file)
                    val mimeType = StickerFileTypeDetector.getMimeType(format)
                    
                    // Copy to cache for safe access
                    val cachedFile = copyToCache(file, cacheDir, authority, packTitle, index)
                    
                    UniversalSticker(
                        id = "$authority-$packTitle-$index",
                        sourcePath = file.absolutePath,
                        sourceUri = null,
                        localFile = cachedFile,
                        originalFileName = file.name,
                        emojiList = listOf("😀"),
                        index = index,
                        mimeType = mimeType
                    )
                }.filter { it.localFile.exists() && it.localFile.length() > 0 }

                if (stickers.isNotEmpty()) {
                    packs.add(UniversalStickerPack(
                        id = "wa-internal-$authority-$packTitle",
                        title = packTitle,
                        sourcePackage = null,
                        sourceAuthority = authority,
                        sourceLayer = SourceLayer.WHATSAPP_INTERNAL_PROVIDER_PREFIX,
                        confidence = Confidence.MEDIUM,
                        format = StickerFormat.STATIC,
                        stickers = stickers
                    ))
                }
            }
        }

        return packs
    }

    private fun copyToCache(file: File, cacheDir: File, authority: String, packName: String, index: Int): File {
        val packDir = File(cacheDir, "wa_internal/${authority}/$packName")
        if (!packDir.exists()) packDir.mkdirs()
        
        val ext = file.extension.ifBlank { "webp" }
        val destFile = File(packDir, "sticker_${index.toString().padStart(2, '0')}.$ext")
        
        if (!destFile.exists()) {
            try {
                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e("WAInternal", "Copy failed: ${file.name}", e)
            }
        }
        
        return destFile
    }
}
