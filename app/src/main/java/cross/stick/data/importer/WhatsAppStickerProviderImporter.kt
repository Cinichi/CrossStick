package cross.stick.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class WhatsAppStickerProviderImporter(private val context: Context) {

    fun importPacks(source: DiscoveredStickerSource): List<UniversalStickerPack> {
        val packs = mutableListOf<UniversalStickerPack>()
        val authority = source.authority
        val metadataUri = Uri.parse("content://$authority/metadata")

        try {
            context.contentResolver.query(metadataUri, null, null, null, null)?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow("sticker_pack_identifier")
                val nameCol = cursor.getColumnIndexOrThrow("sticker_pack_name")
                val pubCol  = cursor.getColumnIndexOrThrow("sticker_pack_publisher")
                val iconCol = cursor.getColumnIndexOrThrow("sticker_pack_icon")
                val animCol = cursor.getColumnIndexOrThrow("animated_sticker_pack")

                while (cursor.moveToNext()) {
                    val packId   = cursor.getString(idCol)
                    val packName = cursor.getString(nameCol)
                    val publisher = cursor.getString(pubCol)
                    val trayFile = cursor.getString(iconCol)
                    val isAnimated = cursor.getInt(animCol) == 1

                    val stickers = importStickers(authority, packId)
                    if (stickers.isEmpty()) continue

                    val format = if (isAnimated) StickerFormat.ANIMATED_TGS else StickerFormat.STATIC

                    packs.add(UniversalStickerPack(
                        id = packId,
                        title = packName,
                        sourceAppPackage = source.packageName,
                        sourceAuthority = authority,
                        sourceType = SourceType.CONTENT_PROVIDER,
                        format = format,
                        stickers = stickers
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("ProviderImporter", "Error importing from ${source.authority}", e)
        }

        return packs
    }

    private fun importStickers(authority: String, packId: String): List<UniversalSticker> {
        val stickers = mutableListOf<UniversalSticker>()
        val stickersUri = Uri.parse("content://$authority/stickers/$packId")

        try {
            context.contentResolver.query(stickersUri, null, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow("sticker_file_name")
                val emojiCol = cursor.getColumnIndexOrThrow("sticker_emoji")

                var index = 0
                while (cursor.moveToNext()) {
                    val fileName = cursor.getString(nameCol)
                    val emojiStr = cursor.getString(emojiCol)
                    val emojis = emojiStr?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: listOf("😀")

                    val assetUri = Uri.parse("content://$authority/stickers_asset/$packId/$fileName")
                    val localFile = copyToLocal(packId, fileName, assetUri) ?: continue

                    val format = StickerFileTypeDetector.detectFormat(localFile)
                    val mimeType = StickerFileTypeDetector.getMimeType(format)

                    stickers.add(UniversalSticker(
                        id = "$packId-$index",
                        file = localFile,
                        originalFileName = fileName,
                        emojiList = emojis,
                        index = index,
                        width = null,
                        height = null,
                        mimeType = mimeType
                    ))
                    index++
                }
            }
        } catch (e: Exception) {
            Log.e("ProviderImporter", "Error importing stickers for $packId", e)
        }

        return stickers
    }

    private fun copyToLocal(packId: String, fileName: String, assetUri: Uri): File? {
        return try {
            val outputDir = File(context.filesDir, "imported/$packId")
            if (!outputDir.exists()) outputDir.mkdirs()
            val outFile = File(outputDir, fileName)

            context.contentResolver.openInputStream(assetUri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (outFile.exists() && outFile.length() > 0) outFile else null
        } catch (e: Exception) {
            Log.e("ProviderImporter", "Error copying $fileName", e)
            null
        }
    }
}
