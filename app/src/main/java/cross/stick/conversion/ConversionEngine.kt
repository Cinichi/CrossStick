package cross.stick.conversion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.media.MediaMetadataRetriever
import android.os.Build
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

object ConversionEngine {

    private const val MAX_STATIC_STICKER_BYTES = 100 * 1024
    private const val MAX_ANIMATED_STICKER_BYTES = 500 * 1024
    private const val STICKER_SIZE = 512

    fun convertToWhatsAppStatic(
        inputFile: File,
        outputDir: File,
        outputName: String
    ): Result<File> {
        return try {
            val outFile = File(outputDir, outputName)
            var success = false
            var isAnimated = false

            // 1. Try converting Animated WebM directly via FFmpeg
            if (isWebM(inputFile)) {
                success = convertWebMToAnimatedWebP(inputFile, outFile)
                isAnimated = success
            } 
            // 2. Try converting Animated TGS (Lottie) via Frame Dumping + FFmpeg
            else if (isGzip(inputFile)) {
                success = convertTgsToAnimatedWebP(inputFile, outFile)
                isAnimated = success
            }

            // 3. Fallback to Static Image if it's a standard image or if FFmpeg failed
            if (!success) {
                val bitmap = tryDecodeBitmap(inputFile) ?: generatePlaceholder(inputFile)
                val scaledBitmap = scaleTo512Transparent(bitmap)
                success = writeWebp(scaledBitmap, outFile)
                scaledBitmap.recycle()
                bitmap.recycle()
                isAnimated = false
            }

            if (success && validateSticker(outFile, isAnimated)) {
                Result.success(outFile)
            } else {
                outFile.delete()
                Result.failure(Exception("Sticker validation failed after conversion"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertWebMToAnimatedWebP(inputFile: File, outFile: File): Boolean {
        val command = "-i \"${inputFile.absolutePath}\" -vcodec libwebp -vf \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000\" -lossless 0 -compression_level 4 -q:v 50 -loop 0 -preset default -an -vsync 0 \"${outFile.absolutePath}\""
        val rc = FFmpeg.execute(command)
        return rc == Config.RETURN_CODE_SUCCESS
    }

    private fun convertTgsToAnimatedWebP(inputFile: File, outFile: File): Boolean {
        try {
            val inputStream = GZIPInputStream(FileInputStream(inputFile))
            val result = LottieCompositionFactory.fromJsonInputStreamSync(inputStream, inputFile.absolutePath)
            val composition = result.value ?: return false

            val framesDir = File(inputFile.parentFile, "tgs_frames_${System.currentTimeMillis()}").apply { mkdirs() }
            val frameCount = 30 // Render 30 frames for a clean loop
            val drawable = LottieDrawable().apply {
                this.composition = composition
                setBounds(0, 0, STICKER_SIZE, STICKER_SIZE)
            }

            val bitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            for (i in 0 until frameCount) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                drawable.progress = i / (frameCount - 1).toFloat()
                drawable.draw(canvas)

                val frameFile = File(framesDir, String.format("frame_%03d.png", i))
                FileOutputStream(frameFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            bitmap.recycle()

            val command = "-framerate 30 -i \"${framesDir.absolutePath}/frame_%03d.png\" -vcodec libwebp -filter:v \"scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000\" -lossless 0 -compression_level 4 -q:v 50 -loop 0 -an \"${outFile.absolutePath}\""
            val rc = FFmpeg.execute(command)
            
            framesDir.deleteRecursively() // Clean up temp frames
            return rc == Config.RETURN_CODE_SUCCESS
        } catch (e: Exception) {
            return false
        }
    }

    private fun tryDecodeBitmap(file: File): Bitmap? {
        if (isWebM(file)) return tryExtractVideoFrame(file)
        return try { BitmapFactory.decodeFile(file.absolutePath) } catch (e: Exception) { null }
    }

    private fun tryExtractVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(0)
        } catch (e: Exception) { null } finally {
            try { retriever.release() } catch (_: Exception) {}
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
            bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
        } catch (e: Exception) { false }
    }

    private fun generatePlaceholder(file: File): Bitmap {
        val bitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = (file.name.hashCode().and(0xFFFF) % 360).toFloat()
        val hsv = floatArrayOf(hue, 0.4f, 0.95f)
        val bgColor = Color.HSVToColor(200, hsv)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(32f, 32f, (STICKER_SIZE - 32).toFloat(), (STICKER_SIZE - 32).toFloat(), 64f, 64f, paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 160f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("✦", STICKER_SIZE / 2f, STICKER_SIZE / 2f + 56f, textPaint)
        return bitmap
    }

    fun validateSticker(file: File, isAnimated: Boolean): Boolean {
        val maxSize = if (isAnimated) MAX_ANIMATED_STICKER_BYTES else MAX_STATIC_STICKER_BYTES
        return file.exists() && file.length() in 1..maxSize
    }

    private fun scaleTo512Transparent(original: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val ratio = minOf(STICKER_SIZE.toFloat() / original.width, STICKER_SIZE.toFloat() / original.height)
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
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
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
        return true
    }
}
