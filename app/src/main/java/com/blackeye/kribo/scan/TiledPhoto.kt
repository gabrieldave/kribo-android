package com.blackeye.kribo.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** The caller owns the private, seekable file. One owner thread at a time. */
class TiledPhoto private constructor(
    private val decoder: BitmapRegionDecoder, private val orientation: Orientation
) : BookPixels, Closeable {
    override val width = orientation.width
    override val height = orientation.height
    private val slots = Array(8) { IntArray(SIDE * SIDE) }
    private val keys = IntArray(slots.size) { -1 }
    private val ages = LongArray(slots.size)
    private val raw = IntArray(SIDE * SIDE)
    private val tileColumns = (width + SIDE - 1) / SIDE
    private var hotKey = -1
    private var hot = 0
    private var tick = 0L
    private var closed = false
    private val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        inScaled = false
        inSampleSize = 1
    }

    // Callers supply in-bounds coordinates; the numeric sampler clamps at the page boundary.
    override fun argb(x: Int, y: Int): Int {
        val key = (y / SIDE) * tileColumns + x / SIDE
        if (key != hotKey) {
            check(!closed)
            var slot = keys.indexOf(key)
            if (slot < 0) {
                slot = 0
                for (i in 1 until slots.size) if (ages[i] < ages[slot]) slot = i
                // A decoder failure must not leave a partially overwritten tile tagged as valid.
                keys[slot] = -1
                hotKey = -1
                load(x - x % SIDE, y - y % SIDE, slots[slot])
                keys[slot] = key
            }
            hot = slot
            hotKey = key
            ages[slot] = ++tick
        }
        return slots[hot][(y % SIDE) * SIDE + x % SIDE]
    }

    private fun load(left: Int, top: Int, dst: IntArray) {
        val w = min(SIDE, width-left)
        val h = min(SIDE, height-top)
        val x1 = orientation.rawX(left,top)
        val y1 = orientation.rawY(left,top)
        val x2 = orientation.rawX(left+w-1,top+h-1)
        val y2 = orientation.rawY(left+w-1,top+h-1)
        val rect = Rect(min(x1,x2), min(y1,y2), max(x1,x2)+1, max(y1,y2)+1)
        val tile = decoder.decodeRegion(rect, options) ?: throw IOException("region decode")
        try {
            check(tile.width == rect.width() && tile.height == rect.height())
            val rawStride = tile.width
            tile.getPixels(raw, 0, rawStride, 0, 0, rawStride, tile.height)
            if (orientation.tag == 1 && !tile.hasAlpha()) {
                for (dy in 0 until h) System.arraycopy(raw,dy*rawStride,dst,dy*SIDE,w)
                return
            }
            for (dy in 0 until h) for (dx in 0 until w) {
                val sx = orientation.rawX(left+dx,top+dy) - rect.left
                val sy = orientation.rawY(left+dx,top+dy) - rect.top
                val value = raw[sy * rawStride + sx]
                dst[dy * SIDE + dx] = overWhite(value)
            }
        } finally {
            tile.recycle()
        }
    }

    override fun close() {
        if (!closed) {
            hotKey = -1
            closed = true
            decoder.recycle()
        }
    }

    companion object {
        private const val SIDE = 256
        @Suppress("DEPRECATION")
        fun open(file: File): TiledPhoto {
            val path = file.absolutePath
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path,bounds)
            require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768 &&
                bounds.outWidth.toLong()*bounds.outHeight <= 64_000_000L) { "invalid image bounds" }
            val decoder = if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(path)
                else BitmapRegionDecoder.newInstance(path, false)
            val rd = decoder ?: throw IOException("unsupported image for tiled decode")
            try {
                require(rd.width in 1..32768 && rd.height in 1..32768 &&
                    rd.width.toLong() * rd.height <= 64_000_000L) { "image outside supported bounds" }
                val tag = ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                return TiledPhoto(rd, Orientation(rd.width,rd.height, if (tag in 1..8) tag else 1))
            } catch (failure: Throwable) {
                rd.recycle()
                throw failure
            }
        }

        private fun overWhite(p: Int): Int {
            val a = p ushr 24
            if (a == 255) return p
            val back = 255*(255-a) + 127
            val r = (((p ushr 16 and 255)*a + back)/255)
            val g = (((p ushr 8 and 255)*a + back)/255)
            val b = (((p and 255)*a + back)/255)
            return (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
