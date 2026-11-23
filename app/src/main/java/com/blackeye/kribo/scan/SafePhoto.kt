package com.blackeye.kribo.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.io.Closeable
import java.io.File

/**
 * Tiled decoding is preferred (50 MP + P3), but some Pixel JPEG_R/Ultra HDR
 * files make BitmapRegionDecoder return null. This wrapper falls back to a
 * full ARGB decode instead of aborting the whole scan.
 */
class SafePhoto private constructor(
    private val delegate: BookPixels,
    private val closeable: Closeable?,
) : BookPixels, Closeable {
    override val width get() = delegate.width
    override val height get() = delegate.height
    override fun argb(x: Int, y: Int) = delegate.argb(x, y)
    override fun close() {
        runCatching { closeable?.close() }
    }

    companion object {
        fun open(file: File): SafePhoto {
            runCatching { TiledPhoto.open(file) }.getOrNull()?.let { photo ->
                return SafePhoto(photo, photo)
            }
            val full = FullBitmapPixels(file)
            return SafePhoto(full, full)
        }
    }
}

private class FullBitmapPixels(file: File) : BookPixels, Closeable {
    private val bitmap: Bitmap = decode(file)
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
    override val width = bitmap.width
    override val height = bitmap.height
    override fun argb(x: Int, y: Int) = bitmap.getPixel(x, y)

    private companion object {
        fun decode(file: File): Bitmap {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                },
            )?.let { return it }
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(file)
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }
            throw IllegalStateException("unsupported image format")
        }
    }
}
