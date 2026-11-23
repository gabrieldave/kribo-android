package com.blackeye.kribo.scan

import android.graphics.Bitmap

/**
 * Bridges the pure [BookPixels] pipeline to Android bitmaps.
 *
 * [BookMath.render] already resamples with the same bilinear math used by UVDoc, so the
 * identity grid produces a high-quality scaled crop without an extra intermediate bitmap.
 */
fun BookMath.renderToBitmap(
    page: BookPixels,
    grid: FloatArray,
    width: Int = page.width,
    height: Int = page.height,
    checkpoint: () -> Unit = {},
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        render(page, grid, width, height, checkpoint = checkpoint) { firstY, count, argb ->
            bitmap.setPixels(argb, 0, width, 0, firstY, width, count)
        }
    } catch (throwable: Throwable) {
        bitmap.recycle()
        throw throwable
    }
    return bitmap
}

fun BookPixels.toBitmap(): Bitmap =
    BookMath.renderToBitmap(this, BookMath.identityGrid())

/** Identity resample to a bounded long side; keeps large camera photos inside memory. */
fun BookPixels.toScaledBitmap(maxLongSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxLongSide) return toBitmap()
    val ratio = maxLongSide.toFloat() / longest
    val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
    val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
    return BookMath.renderToBitmap(this, BookMath.identityGrid(), targetWidth, targetHeight)
}
