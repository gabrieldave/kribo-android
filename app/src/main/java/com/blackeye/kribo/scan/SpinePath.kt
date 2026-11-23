package com.blackeye.kribo.scan

import kotlin.math.abs
import kotlin.math.roundToInt

/** Cost per proxy pixel is in [0,1]. Input is already restricted to the book's bounding rectangle. */
object SpinePath {
    class Path(val column: IntArray, val averageCost: Float)
    class Candidates(val best: Path, val alternative: Path?) {
        val gap: Float? get() = alternative?.let { it.averageCost - best.averageCost }
    }

    fun candidates(cost: FloatArray, w: Int, h: Int): Candidates? {
        require(w in 12..600 && h in 2..400 && cost.size == w*h)
        require(cost.all { it.isFinite() && it in 0f..1f })
        val best = solve(cost,w,h,null) ?: return null
        return Candidates(best,solve(cost,w,h,best.column))
    }

    private fun solve(cost: FloatArray, w: Int, h: Int, excluded: IntArray?): Path? {
        val radius = 2
        val directions = 2*radius+1
        val start = (0.2f*(w-1)).roundToInt()
        val end = (0.8f*(w-1)).roundToInt()
        val alternateDistance = maxOf(2,(0.04f*w).roundToInt())
        var prev = FloatArray(w*directions) { Float.POSITIVE_INFINITY }
        var next = FloatArray(prev.size)
        val back = ByteArray(h*w*directions)
        for (x in start..end) {
            if (excluded == null || abs(x-excluded[0]) >= alternateDistance)
                prev[x*directions+radius] = cost[x]
        }
        for (y in 1 until h) {
            next.fill(Float.POSITIVE_INFINITY)
            for (x in start..end) {
                if (excluded != null && abs(x-excluded[y]) < alternateDistance) continue
                for (d in -radius..radius) {
                    val before = x-d
                    if (before !in start..end) continue
                    val state = x*directions + d+radius
                    for (p in -radius..radius) {
                        val score = prev[before*directions+p+radius] + cost[y*w+x] +
                            0.025f*abs(d) + 0.04f*abs(d-p)
                        if (score < next[state]) {
                            next[state] = score
                            back[y*w*directions+state] = p.toByte()
                        }
                    }
                }
            }
            val old = prev; prev = next; next = old
        }
        var last = 0
        for (i in 1 until prev.size) if (prev[i] < prev[last]) last = i
        if (!prev[last].isFinite()) return null
        val route = IntArray(h)
        var x = last / directions
        var d = last % directions - radius
        for (y in h-1 downTo 1) {
            route[y] = x
            val p = back[(y*w+x)*directions+d+radius].toInt()
            x -= d
            d = p
        }
        route[0] = x
        return Path(route,prev[last]/h)
    }
}

/** Per-row inclusive intervals in a rectangular, upright PageWindow, in local pixels.
 *  Use a conservative overlap on the two inner sides. No second horizontal rescaling.
 *  A missing row can be indicated by first > last. Arrays must remain immutable during the job.
 */
class PaperMaskedPage(
    private val roi: PageWindow, private val first: IntArray, private val last: IntArray,
    private val opaquePaper: IntArray
) : BookPixels {
    override val width = roi.width
    override val height = roi.height
    init { require(first.size == height && last.size == height && opaquePaper.size == height) }
    override fun argb(x: Int, y: Int): Int =
        if (x >= first[y] && x <= last[y]) roi.argb(x,y) else (opaquePaper[y] or (255 shl 24))
}
