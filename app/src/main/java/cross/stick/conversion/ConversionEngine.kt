package cross.stick.conversion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

object ConversionEngine {

    private const val MAX_STATIC_STICKER_BYTES = 100 * 1024
    private const val STICKER_SIZE = 512

    /**
     * Converts any input file (WebP, PNG, JPEG, or TGS/WebM animated) into a
     * WhatsApp-compatible 512x512 static WebP under 100 KB.
     *
     * For TGS (Lottie/gzip) and WebM files that Android cannot bitmap-decode
     * directly, a solid-color placeholder tile is generated so the pack still
     * builds. The emoji colour is derived from the file name hash so each
     * placeholder looks distinct.
     */
    fun convertToWhatsAppStatic(
        inputFile: File,
        outputDir: File,
        outputName: String
    ): Result<File> {
        return try {
            val bitmap = tryDecodeBitmap(inputFile)
                ?: generatePlaceholder(inputFile)

            val scaledBitmap = scaleTo512Transparent(bitmap)
            val outFile = File(outputDir, outputName)
            val success = writeWebp(scaledBitmap, outFile)

            scaledBitmap.recycle()
            bitmap.recycle()

            if (success && validateStaticSticker(outFile)) {
                Result.success(outFile)
            } else {
                outFile.delete()
                Result.failure(Exception("Sticker validation failed after conversion"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Try to decode the file as a bitmap.
     * For TGS (gzip-compressed Lottie JSON) we can't render on Android without
     * an external library, so return null and fall back to placeholder.
     * For WebM we also fall back.
     */
    private fun tryDecodeBitmap(file: File): Bitmap? {
        // Detect TGS by gzip magic bytes
        if (isGzip(file)) return null
        // Detect WebM by EBML magic bytes
        if (isWebM(file)) return null

        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    private fun isGzip(file: File): Boolean {
        return try {
            val bytes = file.inputStream().use { it.readNBytes(2) }
            bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
        } catch (e: Exception) { false }
    }

    private fun isWebM(file: File): Boolean {
        return try {
            val bytes = file.inputStream().use { it.readNBytes(4) }
            bytes.size >= 4 &&
                bytes[0] == 0x1A.toByte() &&
                bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() &&
                bytes[3] == 0xA3.toByte()
        } catch (e: Exception) { false }
    }

    /**
     * Generate a solid-color 512x512 placeholder for sticker formats Android
     * cannot decode (TGS, WebM). Uses a pastel colour derived from the filename
     * so each placeholder is visually distinct.
     */
    private fun generatePlaceholder(file: File): Bitmap {
        val bitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Pick a pastel colour from filename hash
        val hue = (file.name.hashCode().and(0xFFFF) % 360).toFloat()
        val hsv = floatArrayOf(hue, 0.4f, 0.95f)
        val bgColor = Color.HSVToColor(200, hsv) // semi-transparent

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(
            32f, 32f,
            (STICKER_SIZE - 32).toFloat(),
            (STICKER_SIZE - 32).toFloat(),
            64f, 64f, paint
        )

        // Draw animated sticker symbol in the centre
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 160f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("✦", STICKER_SIZE / 2f, STICKER_SIZE / 2f + 56f, textPaint)

        return bitmap
    }

    fun validateStaticSticker(file: File): Boolean {
        if (!file.exists() || file.length() !in 1..MAX_STATIC_STICKER_BYTES) return false
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth == STICKER_SIZE && opts.outHeight == STICKER_SIZE
    }

    private fun scaleTo512Transparent(original: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val ratio = minOf(
            STICKER_SIZE.toFloat() / original.width,
            STICKER_SIZE.toFloat() / original.height
        )
        val newW = (original.width * ratio).toInt().coerceAtLeast(1)
        val newH = (original.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, newW, newH, true)
        val left = (STICKER_SIZE - newW) / 2
        val top = (STICKER_SIZE - newH) / 2
        canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
        scaled.recycle()
        return result
    }

    private fun writeWebp(bitmap: Bitmap, output: File): Boolean {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        var quality = 80
        while (quality >= 10) {
            FileOutputStream(output).use { fos -> bitmap.compress(format, quality, fos) }
            if (output.length() in 1..MAX_STATIC_STICKER_BYTES) return true
            quality -= 10
        }
        output.delete()
        return false
    }

    fun createTrayFromFile(inputFile: File, outputDir: File): Result<File> {
        return try {
            val bitmap = tryDecodeBitmap(inputFile) ?: generatePlaceholder(inputFile)
            val tray = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            bitmap.recycle()
            val trayFile = File(outputDir, "tray.png")
            if (!tryWriteTrayPng(tray, trayFile)) {
                tray.recycle()
                return Result.failure(Exception("Tray too large"))
            }
            tray.recycle()
            Result.success(trayFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryWriteTrayPng(tray: Bitmap, output: File): Boolean {
        FileOutputStream(output).use { fos -> tray.compress(Bitmap.CompressFormat.PNG, 100, fos) }
        if (output.length() in 1..(50 * 1024)) return true
        output.delete()
        return false
    }

    fun validateTray(file: File): Boolean {
        if (!file.exists() || file.length() !in 1..(50 * 1024)) return false
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth == 96 && opts.outHeight == 96
    }
}
