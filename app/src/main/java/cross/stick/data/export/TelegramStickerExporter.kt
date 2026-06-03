package cross.stick.data.export

import android.content.Context
import android.util.Log
import cross.stick.data.importer.UniversalStickerPack
import cross.stick.data.importer.StickerFormat
import cross.stick.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramStickerExporter(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun exportPack(pack: UniversalStickerPack): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getBotToken() ?: return@withContext Result.failure(Exception("Bot token not set"))
            val packName = "${pack.id}_by_${getBotUsername(token)}"
            val uploadedStickers = mutableListOf<JSONObject>()

            pack.stickers.forEachIndexed { index, sticker ->
                val fileId = uploadStickerFile(token, sticker.localFile)
                if (fileId != null) {
                    val stickerObj = JSONObject().apply {
                        put("sticker", "attach://sticker$index")
                        put("emoji_list", JSONArray(sticker.emojiList))
                        put("format", when (sticker.localFile.extension.lowercase()) {
                            "tgs" -> "animated"
                            "webm" -> "video"
                            else -> "static"
                        })
                    }
                    uploadedStickers.add(stickerObj)
                }
            }

            if (uploadedStickers.isEmpty()) {
                return@withContext Result.failure(Exception("No stickers could be uploaded"))
            }

            createStickerSet(token, packName, pack.title, uploadedStickers, pack.format)
            Result.success("https://t.me/addstickers/$packName")
        } catch (e: Exception) {
            Log.e("TelegramExport", "Export failed", e)
            Result.failure(e)
        }
    }

    private suspend fun uploadStickerFile(token: String, file: java.io.File): String? {
        return try {
            val mediaType = when (file.extension.lowercase()) {
                "png" -> "image/png".toMediaTypeOrNull()
                "tgs" -> "application/x-tgsticker".toMediaTypeOrNull()
                "webm" -> "video/webm".toMediaTypeOrNull()
                else -> "image/webp".toMediaTypeOrNull()
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sticker", file.name, file.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/uploadStickerFile")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            
            if (json.getBoolean("ok")) {
                json.getJSONObject("result").getString("file_id")
            } else {
                Log.e("TelegramExport", "Upload failed: ${json.optString("description")}")
                null
            }
        } catch (e: Exception) {
            Log.e("TelegramExport", "Upload error", e)
            null
        }
    }

    private suspend fun createStickerSet(
        token: String, name: String, title: String,
        stickers: List<JSONObject>, format: StickerFormat
    ): Boolean {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("title", title)
                put("stickers", JSONArray(stickers))
                put("sticker_format", when (format) {
                    StickerFormat.STATIC -> "static"
                    StickerFormat.ANIMATED_TGS -> "animated"
                    StickerFormat.VIDEO_WEBM -> "video"
                })
            }

            val requestBody = okhttp3.RequestBody.create(
                "application/json".toMediaTypeOrNull(), json.toString()
            )

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/createNewStickerSet")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            return JSONObject(response.body?.string() ?: "").getBoolean("ok")
        } catch (e: Exception) { return false }
    }

    private suspend fun getBotToken(): String? {
        val token = PreferencesManager(context).botToken.first()
        return token.ifBlank { null }
    }

    private suspend fun getBotUsername(token: String): String {
        return try {
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/getMe")
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (json.getBoolean("ok")) json.getJSONObject("result").getString("username") else "bot"
        } catch (e: Exception) { "bot" }
    }
}
