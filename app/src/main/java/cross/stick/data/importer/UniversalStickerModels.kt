package cross.stick.data.importer

import android.net.Uri
import java.io.File

data class UniversalStickerPack(
    val id: String,
    val title: String,
    val sourcePackage: String?,
    val sourceAuthority: String?,
    val sourceLayer: SourceLayer,
    val confidence: Confidence,
    val format: StickerFormat,
    val stickers: List<UniversalSticker>
)

data class UniversalSticker(
    val id: String,
    val sourcePath: String?,
    val sourceUri: Uri?,
    val localFile: File,
    val originalFileName: String,
    val emojiList: List<String>,
    val index: Int,
    val mimeType: String
)

enum class SourceLayer {
    CONTENT_PROVIDER,
    APK_ASSETS,
    ROOT_APP_DATABASE,
    WHATSAPP_INTERNAL_PROVIDER_PREFIX,
    WHATSAPP_MEDIA_FOLDER,
    DATE_HASH_FALLBACK
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

enum class StickerFormat {
    STATIC,
    ANIMATED_TGS,
    VIDEO_WEBM
}
