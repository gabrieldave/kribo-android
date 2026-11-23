package com.blackeye.kribo.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ML Kit Text Recognition V2 bundled model (no dynamic download, no Play Services module).
 *
 * Used as the secondary engine of the ScanOffline pipeline: the page is decoded at up to
 * 2800 px, EXIF rotation is applied, and words are reconstructed from each native line.
 * The recognizer is created lazily the first time this engine is used, so it never touches
 * the cold-start path.
 */
class MlKitBundledOcrEngine : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(file: File): String? = withContext(Dispatchers.Default) {
        if (!file.isFile) return@withContext null
        val bitmap = decodeForOcr(file) ?: return@withContext null
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(recognizer.process(image), 30, TimeUnit.SECONDS)
            val blocks = result.textBlocks.sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
            val text = buildString {
                for (block in blocks) {
                    val lines = block.lines.sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
                    for (line in lines) {
                        val words = line.elements
                            .sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE }
                            .joinToString(" ") { it.text.trim() }
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        if (words.isNotEmpty()) {
                            if (isNotEmpty()) append('\n')
                            append(words)
                        }
                    }
                }
            }
            OcrTextSanitizer.cleanForOcr(text).takeIf { it.isNotBlank() }
        } catch (throwable: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        runCatching { recognizer.close() }
    }

    private fun decodeForOcr(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= ML_KIT_LONG_SIDE) sample *= 2
        var bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            },
        ) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        }.getOrDefault(1)
        val degrees = when (orientation) {
            3 -> 180
            6 -> 90
            8 -> 270
            else -> 0
        }
        if (degrees != 0) {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        val scaled = scaleDown(bitmap, ML_KIT_LONG_SIDE)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun scaleDown(bitmap: Bitmap, maxLongSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxLongSide) return bitmap
        val ratio = maxLongSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * ratio).roundToInt()),
            max(1, (bitmap.height * ratio).roundToInt()),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private companion object {
        const val ML_KIT_LONG_SIDE = 2800
    }
}
