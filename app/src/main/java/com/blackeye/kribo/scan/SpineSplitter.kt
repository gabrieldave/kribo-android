package com.blackeye.kribo.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Split an open-book photo into one or two full-resolution page views. */
object SpineSplitter {

    class Page(val image: BookPixels, val label: String)

    fun split(source: BookPixels): List<Page> {
        if (source.width < 80 || source.height < 80) return listOf(Page(PageWindow(source, 0, 0, source.width, source.height), "Página"))
        val proxyW = min(220, source.width)
        val proxyH = max(12, min(360, (source.height.toFloat() * proxyW / source.width).roundToInt()))
        val stepX = source.width.toFloat() / proxyW
        val stepY = source.height.toFloat() / proxyH
        val cost = FloatArray(proxyW * proxyH)
        for (y in 0 until proxyH) {
            val sy = ((y + 0.5f) * stepY).toInt().coerceIn(0, source.height - 1)
            for (x in 0 until proxyW) {
                val sx = ((x + 0.5f) * stepX).toInt().coerceIn(0, source.width - 1)
                val p = source.argb(sx, sy)
                val lum = (((p shr 16 and 255) * 299 + ((p shr 8 and 255) * 587) + ((p and 255) * 114)) / 1000f) / 255f
                // Dark valley at the spine plus a weak center prior.
                val center = abs((x + 0.5f) / proxyW - 0.5f) * 2f
                cost[y * proxyW + x] = (0.62f * (1f - lum) + 0.30f * center + 0.08f).coerceIn(0f, 1f)
            }
        }
        val candidates = runCatching { SpinePath.candidates(cost, proxyW, proxyH) }.getOrNull()
        val path = candidates?.best
        val useSplit = path != null && path.averageCost.isFinite()
        if (!useSplit) return listOf(Page(PageWindow(source, 0, 0, source.width, source.height), "Página"))
        val scale = source.width.toFloat() / proxyW
        val boundary = IntArray(source.height) { y ->
            val py = (y.toFloat() / source.height * proxyH).toInt().coerceIn(0, proxyH - 1)
            (path!!.column[py] * scale).roundToInt().coerceIn(0, source.width - 1)
        }
        val margin = max((source.width * 0.006f).roundToInt(), (source.width * 0.012f).roundToInt())
            .coerceAtMost((source.width * 0.02f).roundToInt().coerceAtLeast(1))
        val leftRight = (boundary.maxOrNull() ?: source.width / 2) + margin
        val rightLeft = (boundary.minOrNull() ?: source.width / 2) - margin
        if (leftRight < source.width / 5 || rightLeft > source.width * 4 / 5) {
            return listOf(Page(PageWindow(source, 0, 0, source.width, source.height), "Página"))
        }
        val leftRoi = PageWindow(source, 0, 0, leftRight.coerceIn(1, source.width), source.height)
        val rightRoi = PageWindow(source, rightLeft.coerceIn(0, source.width - 1), 0, source.width, source.height)
        val leftIntervals = IntArray(source.height) { y -> (boundary[y] + margin).coerceIn(0, leftRoi.width - 1) }
        val rightFirstLocal = IntArray(source.height) { y -> (boundary[y] - margin - rightLeft).coerceIn(0, rightRoi.width - 1) }
        val leftPaper = IntArray(source.height) { y -> paperAt(source, (leftRight * 0.55f).toInt(), y) }
        val rightPaper = IntArray(source.height) { y ->
            paperAt(source, (rightLeft + (source.width - rightLeft) * 0.45f).toInt(), y)
        }
        return listOf(
            Page(
                PaperMaskedPage(leftRoi, IntArray(source.height) { 0 }, leftIntervals, leftPaper),
                "Página izquierda",
            ),
            Page(
                PaperMaskedPage(rightRoi, rightFirstLocal, IntArray(source.height) { rightRoi.width - 1 }, rightPaper),
                "Página derecha",
            ),
        )
    }

    private fun paperAt(source: BookPixels, x: Int, y: Int): Int {
        val p = source.argb(x.coerceIn(0, source.width - 1), y.coerceIn(0, source.height - 1))
        val lum = ((p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114) / 1000
        return if (lum in 150..253) p else 0xFFFFFFFF.toInt()
    }
}
