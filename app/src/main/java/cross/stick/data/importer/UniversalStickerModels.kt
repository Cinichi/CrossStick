package cross.stick.data.importer

import java.io.File

data class UniversalStickerPack(
    val id: String,
    val title: String,
    val sourceAppPackage: String?,
    val sourceAuthority: String?,
    val sourceType: SourceType,
    val format: StickerFormat,
    val stickers: List<UniversalSticker>
)

data class UniversalSticker(
    val id: String,
    val file: File,
    val originalFileName: String,
    val emojiList: List<String>,
    val index: Int,
    val width: Int?,
    val height: Int?,
    val mimeType: String
)

enum class SourceType {
    CONTENT_PROVIDER,
    STICKERCONV_DATABASE,
    WHATSAPP_DATABASE,
    WHATSAPP_MEDIA_FOLDER,
    MANUAL_FOLDER
}

enum class StickerFormat {
    STATIC,
    ANIMATED_TGS,
    VIDEO_WEBM
}
