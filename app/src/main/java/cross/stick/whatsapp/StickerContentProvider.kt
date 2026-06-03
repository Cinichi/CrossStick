package cross.stick.whatsapp

import android.content.ContentProvider
import android.content.Context
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import cross.stick.data.local.PreferencesManager
import java.io.File

class StickerContentProvider : ContentProvider() {

    companion object {
        const val PROVIDER_NAME = "cross.stick.stickercontentprovider"
        private const val TAG = "CrossStickProvider"

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        private const val METADATA_CODE = 1
        private const val METADATA_SINGLE_CODE = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4

        private const val MAX_WHATSAPP_STICKERS = 30
        private const val MIN_WHATSAPP_STICKERS = 3

        private const val STICKER_PACK_IDENTIFIER = "sticker_pack_identifier"
        private const val STICKER_PACK_NAME = "sticker_pack_name"
        private const val STICKER_PACK_PUBLISHER = "sticker_pack_publisher"
        private const val STICKER_PACK_ICON = "sticker_pack_icon"
        private const val ANDROID_PLAY_STORE_LINK = "android_play_store_link"
        private const val IOS_APP_DOWNLOAD_LINK = "ios_app_download_link"
        private const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        private const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        private const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        private const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        private const val IMAGE_DATA_VERSION = "image_data_version"
        private const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        private const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        private const val STICKER_FILE_NAME = "sticker_file_name"
        private const val STICKER_EMOJI = "sticker_emoji"
        private const val STICKER_ACCESSIBILITY_TEXT = "sticker_accessibility_text"

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(PROVIDER_NAME, METADATA, METADATA_CODE)
            addURI(PROVIDER_NAME, "$METADATA/*", METADATA_SINGLE_CODE)
            addURI(PROVIDER_NAME, "$STICKERS/*", STICKERS_CODE)
            addURI(PROVIDER_NAME, "$STICKERS_ASSET/*/*", STICKERS_ASSET_CODE)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (matcher.match(uri)) {
            METADATA_CODE -> getMetadataCursor(uri, null)
            METADATA_SINGLE_CODE -> getMetadataCursor(uri, uri.lastPathSegment)
            STICKERS_CODE -> getStickersCursor(uri, uri.lastPathSegment)
            else -> {
                Log.w(TAG, "Unknown query uri: $uri")
                null
            }
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != STICKERS_ASSET_CODE || !mode.contains("r")) return null

        val segments = uri.pathSegments
        if (segments.size != 3) return null

        val packId = segments[1]
        val fileName = segments[2]
        val file = resolveFile(packId, fileName)

        if (!isSafeStickerFile(packId, fileName, file)) {
            Log.w(TAG, "Rejected asset uri: $uri")
            return null
        }

        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open sticker asset: $uri", e)
            null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return openAssetFile(uri, mode)?.parcelFileDescriptor
    }

    override fun getType(uri: Uri): String? {
        return when (matcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$PROVIDER_NAME.$METADATA"
            METADATA_SINGLE_CODE -> "vnd.android.cursor.item/vnd.$PROVIDER_NAME.$METADATA"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$PROVIDER_NAME.$STICKERS"
            STICKERS_ASSET_CODE -> {
                val fileName = uri.lastPathSegment.orEmpty()
                if (fileName.equals("tray.png", ignoreCase = true)) "image/png" else "image/webp"
            }
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getMetadataCursor(uri: Uri, requestedPackId: String?): Cursor {
        val columns = arrayOf(
            STICKER_PACK_IDENTIFIER,
            STICKER_PACK_NAME,
            STICKER_PACK_PUBLISHER,
            STICKER_PACK_ICON,
            ANDROID_PLAY_STORE_LINK,
            IOS_APP_DOWNLOAD_LINK,
            PUBLISHER_EMAIL,
            PUBLISHER_WEBSITE,
            PRIVACY_POLICY_WEBSITE,
            LICENSE_AGREEMENT_WEBSITE,
            IMAGE_DATA_VERSION,
            AVOID_CACHE,
            ANIMATED_STICKER_PACK
        )
        val cursor = MatrixCursor(columns)

        convertedRootDir().listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.filter { requestedPackId == null || it.name == requestedPackId }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { packDir ->
                val stickers = stickerFilesForPack(packDir.name)
                val tray = File(packDir, "tray.png")
                if (stickers.size < MIN_WHATSAPP_STICKERS || !tray.exists()) return@forEach

                cursor.addRow(
                    arrayOf(
                        packDir.name,
                        packDir.name.take(128),
                        publisherName(),
                        "tray.png",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        packDir.lastModified().coerceAtLeast(1L).toString(),
                        0,
                        0
                    )
                )
            }

        context?.contentResolver?.let { cursor.setNotificationUri(it, uri) }
        return cursor
    }

    private fun getStickersCursor(uri: Uri, packId: String?): Cursor {
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME, STICKER_EMOJI, STICKER_ACCESSIBILITY_TEXT))
        if (packId.isNullOrBlank()) return cursor

        stickerFilesForPack(packId).forEachIndexed { index, file ->
            cursor.addRow(arrayOf(file.name, "😀", "Sticker ${index + 1}"))
        }

        context?.contentResolver?.let { cursor.setNotificationUri(it, uri) }
        return cursor
    }

    private fun convertedRootDir(): File {
        return File(requireNotNull(context).filesDir, "stickers/converted")
    }

    private fun publisherName(): String {
        return context
            ?.getSharedPreferences(PreferencesManager.SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(PreferencesManager.SYNC_AUTHOR_NAME, "CrossStick User")
            ?.takeIf { it.isNotBlank() }
            ?.take(128)
            ?: "CrossStick User"
    }

    private fun packDir(packId: String): File {
        return File(convertedRootDir(), packId)
    }

    private fun stickerFilesForPack(packId: String): List<File> {
        val dir = packDir(packId)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file ->
            file.isFile && file.extension.equals("webp", ignoreCase = true) && file.length() in 1..(100L * 1024L)
        }
            ?.sortedBy { it.name }
            ?.take(MAX_WHATSAPP_STICKERS)
            ?: emptyList()
    }

    private fun resolveFile(packId: String, fileName: String): File {
        return File(packDir(packId), fileName)
    }

    private fun isSafeStickerFile(packId: String, fileName: String, file: File): Boolean {
        val canonicalPackDir = packDir(packId).canonicalFile
        val canonicalFile = file.canonicalFile
        if (!canonicalFile.path.startsWith(canonicalPackDir.path + File.separator)) return false
        if (!file.exists() || !file.isFile) return false
        if (fileName == "tray.png") return file.length() in 1..(50L * 1024L)
        return stickerFilesForPack(packId).any { it.name == fileName }
    }
}
