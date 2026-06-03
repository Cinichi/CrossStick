package cross.stick.data.importer

import android.util.Log
import java.io.File
import java.io.FileInputStream

object StickerFileTypeDetector {

    fun detectFormat(file: File): StickerFormat {
        val header = readHeaderBytes(file, 12)
        Log.d("TypeDetector", "File: ${file.name}, header: ${header.joinToString(" ") { "%02X".format(it) }}")
        return when {
            header.startsWith(listOf(0x52, 0x49, 0x46, 0x46)) -> { // RIFF
                // RIFF .... WEBP → check if animated later
                StickerFormat.STATIC
            }
            header.startsWith(listOf(0x1F, 0x8B, 0x08)) -> { // Gzip (TGS)
                StickerFormat.ANIMATED_TGS
            }
            header.startsWith(listOf(0x1A, 0x45, 0xDF, 0xA3)) -> { // EBML (WebM)
                StickerFormat.VIDEO_WEBM
            }
            header.size >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && header[6] == 0x79.toByte() && header[7] == 0x70.toByte() -> {
                StickerFormat.VIDEO_WEBM // fallback WebM/heic etc
            }
            else -> StickerFormat.STATIC
        }
    }

    fun getMimeType(format: StickerFormat): String = when (format) {
        StickerFormat.STATIC -> "image/webp"
        StickerFormat.ANIMATED_TGS -> "application/x-tgsticker"
        StickerFormat.VIDEO_WEBM -> "video/webm"
    }

    private fun readHeaderBytes(file: File, count: Int): List<Byte> {
        return try {
            FileInputStream(file).use { stream ->
                val bytes = ByteArray(count)
                val read = stream.read(bytes)
                if (read > 0) bytes.take(read) else emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun List<Byte>.startsWith(prefix: List<Int>): Boolean {
        if (size < prefix.size) return false
        return prefix.withIndex().all { (i, b) -> this[i].toInt() and 0xFF == b }
    }
}
