package com.blackeye.kribo.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background page pipeline ported from ScanOffline's `procesado/`.
 *
 * 1. Decodes the photo with tiled/EXIF-aware access.
 * 2. Runs [SpineSplitter] to split an open book into left/right pages when the photo is a
 *    horizontal spread.
 * 3. Produces a raw candidate and a UVDoc-flattened candidate per page, so the caller can OCR
 *    both and keep the richer result (curved pages win with UVDoc, flat receipts stay raw).
 * 4. Applies the same shadow lift and finger cleanup used by ScanOffline to book pages.
 *
 * No model is loaded until [process] is called; [BookOrt] stages the UVDoc asset lazily.
 */
class DocumentPageProcessor(
    private val context: Context,
    private val maxLongSide: Int = 3600,
) {

    data class PageCandidate(
        val label: String,
        val raw: File,
        val flat: File?,
    )

    suspend fun process(input: File, outputDir: File): List<PageCandidate> =
        withContext(Dispatchers.Default) {
            if (!input.isFile) return@withContext emptyList()
            if (!outputDir.isDirectory && !outputDir.mkdirs() && !outputDir.isDirectory) {
                return@withContext emptyList()
            }

            val options = detectOptions(input)
            SafePhoto.open(input).use { photo ->
                val views = if (options.splitPages) {
                    SpineSplitter.split(photo)
                } else {
                    listOf(
                        SpineSplitter.Page(
                            PageWindow(photo, 0, 0, photo.width, photo.height),
                            "Página",
                        ),
                    )
                }
                val split = views.size > 1
                val out = ArrayList<PageCandidate>(views.size)
                views.forEachIndexed { index, view ->
                    val target = targetSize(view.image.width, view.image.height)
                    val raw = File(outputDir, "page-${index + 1}-raw.jpg")
                    val rawBitmap = view.image.toScaledBitmap(maxLongSide)
                    val rawOk = writeJpeg(rawBitmap, raw)
                    rawBitmap.recycle()
                    if (!rawOk) return@forEachIndexed

                    val flat = if (options.flatten) {
                        val flatFile = File(outputDir, "page-${index + 1}-flat.jpg")
                        flattenAndSave(view.image, target, flatFile, cleanBookPixels = split)?.let {
                            flatFile
                        }
                    } else {
                        null
                    }
                    out += PageCandidate(
                        label = view.label,
                        raw = raw,
                        flat = flat,
                    )
                }
                out
            }
        }

    private suspend fun flattenAndSave(
        page: BookPixels,
        target: Pair<Int, Int>,
        destination: File,
        cleanBookPixels: Boolean,
    ): File? {
        val flattened = runCatching {
            BookOrt.withBook(context) {
                flatten(page, target.first, target.second)
            }
        }.getOrNull() ?: return null

        var working = flattened
        if (cleanBookPixels) {
            val fingers = runCatching { BookPixelOps.removeFingers(working) }.getOrNull()
            if (fingers != null && fingers !== working) {
                working.recycle()
                working = fingers
            }
        }
        val shadows = runCatching { BookPixelOps.liftShadows(working) }.getOrNull()
        if (shadows != null && shadows !== working) {
            working.recycle()
            working = shadows
        }
        val ok = writeJpeg(working, destination)
        working.recycle()
        return if (ok) destination else null
    }

    private fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxLongSide) return width to height
        val ratio = maxLongSide.toFloat() / longest
        return (width * ratio).toInt().coerceAtLeast(1) to
            (height * ratio).toInt().coerceAtLeast(1)
    }

    private fun writeJpeg(bitmap: Bitmap, file: File): Boolean = runCatching {
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        file.isFile && file.length() > 0L
    }.getOrDefault(false).also { if (!it) file.delete() }

    private data class Options(val splitPages: Boolean, val flatten: Boolean)

    /**
     * Kribo has no credential flow, so the ScanOffline "card" guard is intentionally absent:
     * a typical open-book spread (1.4–1.8 aspect) must still split and flatten. Raw and flat
     * candidates are both OCR'd, so receipts are never forced through a bad warp.
     */
    private fun detectOptions(input: File): Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return Options(splitPages = false, flatten = true)
        val spread = width > height * 1.18f
        return Options(
            splitPages = spread,
            flatten = true,
        )
    }

    private companion object {
        const val JPEG_QUALITY = 94
    }
}
