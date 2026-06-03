package cross.stick.data.repository

import android.content.Context
import android.util.Log
import cross.stick.data.local.PreferencesManager
import cross.stick.data.model.TelegramStickerSet
import cross.stick.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StickerRepository(context: Context) {
    private val prefs = PreferencesManager(context)
    private val filesDir = context.filesDir

    suspend fun fetchStickerSet(packName: String): Result<TelegramStickerSet> =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken() ?: return@withContext Result.failure(Exception("Bot token not set"))
                val url = "https://api.telegram.org/bot$token/getStickerSet?name=$packName"
                Log.d("CrossStick", "Fetching: $url")
                val response = RetrofitClient.api.getStickerSet(url)
                if (response.ok && response.result != null) {
                    Result.success(response.result)
                } else {
                    Result.failure(Exception(response.description ?: "Unknown Telegram API error"))
                }
            } catch (e: Exception) {
                Log.e("CrossStick", "Fetch error", e)
                Result.failure(e)
            }
        }

    suspend fun downloadSticker(fileId: String, packId: String, index: Int): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val token = getToken() ?: return@withContext Result.failure(Exception("Bot token not set"))
                val getFileUrl = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                val fileResponse = RetrofitClient.api.getFile(getFileUrl)
                val filePath = fileResponse.result?.file_path
                    ?: return@withContext Result.failure(Exception("Could not get file path"))
                val ext = filePath.substringAfterLast('.', "webp")
                val downloadUrl = "https://api.telegram.org/file/bot$token/$filePath"
                Log.d("CrossStick", "Downloading: $downloadUrl")
                val responseBody = RetrofitClient.api.downloadFile(downloadUrl)
                val rawDir = File(filesDir, "stickers/raw/$packId")
                if (!rawDir.exists()) rawDir.mkdirs()
                val outFile = File(rawDir, "sticker_$index.$ext")
                FileOutputStream(outFile).use { fos ->
                    responseBody.byteStream().copyTo(fos)
                }
                Log.d("CrossStick", "Downloaded: ${outFile.length()} bytes")
                Result.success(outFile)
            } catch (e: Exception) {
                Log.e("CrossStick", "Download error for sticker $index", e)
                Result.failure(e)
            }
        }

    fun extractPackName(input: String): String {
        return input
            .replace("https://t.me/addstickers/", "")
            .replace("t.me/addstickers/", "")
            .trimEnd('/')
            .split("?").first()
    }

    private suspend fun getToken(): String? {
        val token = prefs.botToken.first()
        return token.ifBlank { null }
    }
}
