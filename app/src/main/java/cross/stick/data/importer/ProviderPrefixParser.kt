package cross.stick.data.importer

data class ProviderPrefixedStickerName(
    val authority: String?,
    val assetName: String
)

object ProviderPrefixParser {

    fun parse(fileName: String): ProviderPrefixedStickerName {
        val firstSpace = fileName.indexOf(' ')
        if (firstSpace <= 0) {
            return ProviderPrefixedStickerName(null, fileName)
        }

        val possibleAuthority = fileName.substring(0, firstSpace)
        val assetName = fileName.substring(firstSpace + 1)

        val looksLikeAuthority =
            possibleAuthority.contains('.') &&
            (
                possibleAuthority.contains("provider", ignoreCase = true) ||
                possibleAuthority.contains("sticker", ignoreCase = true) ||
                possibleAuthority.contains("whatsapp", ignoreCase = true)
            )

        return if (looksLikeAuthority) {
            ProviderPrefixedStickerName(possibleAuthority, assetName)
        } else {
            ProviderPrefixedStickerName(null, fileName)
        }
    }

    fun inferPackNameFromAsset(assetName: String): String {
        return assetName
            .removeSuffix(".png")
            .removeSuffix(".webp")
            .removeSuffix(".jpg")
            .replace(Regex("\\.\\d+$"), "")
            .replace(Regex("_by_.*$"), "")
            .replace('_', ' ')
            .trim()
            .ifBlank { "Unknown WhatsApp Stickers" }
    }
}
