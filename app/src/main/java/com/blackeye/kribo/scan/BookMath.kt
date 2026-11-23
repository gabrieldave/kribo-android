package com.blackeye.kribo.scan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.min

/** Opaque, upright, sRGB pixels. Coordinates are centers of pixels. Single worker only. */
interface BookPixels {
    val width: Int
    val height: Int
    fun argb(x: Int, y: Int): Int
}

/** [left,right) and [top,bottom) in the upright image. No bitmap copy. */
class PageWindow(
    private val image: BookPixels, private val left: Int, private val top: Int,
    right: Int, bottom: Int
) : BookPixels {
    override val width = right - left
    override val height = bottom - top
    init {
        require(left >= 0 && top >= 0 && right <= image.width && bottom <= image.height)
        require(width > 0 && height > 0)
    }
    override fun argb(x: Int, y: Int) = image.argb(left + x, top + y)
}

object BookMath {
    const val INPUT_W = 488
    const val INPUT_H = 712
    const val GRID_W = 31
    const val GRID_H = 45
    const val NODES = GRID_W * GRID_H
    const val INPUT_FLOATS = INPUT_W * INPUT_H * 3
    const val GRID_FLOATS = 2 * NODES

    fun inputBuffer(): FloatBuffer = ByteBuffer.allocateDirect(INPUT_FLOATS * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    // F.interpolate(..., align_corners=true) samples source index zero for target size one.
    fun aligned(i: Int, target: Int, source: Int): Float =
        if (target == 1) 0f else (i.toDouble() * (source - 1) / (target - 1)).toFloat()

    // F.grid_sample(..., padding_mode="border", align_corners=true) on finite data.
    fun pixel(z: Float, size: Int): Float =
        ((z + 1f) * 0.5f * (size - 1)).coerceIn(0f, (size - 1).toFloat())

    private class AxisPlan(target: Int, source: Int) {
        val lower = IntArray(target)
        val fraction = FloatArray(target)
        init {
            for (i in 0 until target) {
                val p = aligned(i, target, source)
                lower[i] = p.toInt()
                fraction[i] = p - lower[i]
            }
        }
    }

    fun identityGrid(): FloatArray = FloatArray(GRID_FLOATS) { index ->
        val i = index % NODES
        if (index < NODES) 2f * (i % GRID_W) / (GRID_W - 1) - 1f
        else 2f * (i / GRID_W) / (GRID_H - 1) - 1f
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun channel(c: Int, shift: Int) = ((c ushr shift) and 255).toFloat()
    private fun bilerpChannel(
        a: Int, b: Int, c: Int, d: Int, dx: Float, dy: Float, shift: Int
    ) = mix(
        mix(channel(a, shift), channel(b, shift), dx),
        mix(channel(c, shift), channel(d, shift), dx), dy
    )

    /** Interpolate the 8-bit values in float. No intermediate rounded 8-bit resize. */
    fun packBgr(page: BookPixels, dst: FloatBuffer, checkpoint: () -> Unit = {}) {
        require(page.width > 0 && page.height > 0 && dst.capacity() == INPUT_FLOATS)
        dst.clear()
        val xs = AxisPlan(INPUT_W, page.width)
        val ys = AxisPlan(INPUT_H, page.height)
        val plane = INPUT_W * INPUT_H
        // Blocks improve locality on a rotated 48 MP region decoder.
        for (by in 0 until INPUT_H step 16) {
            for (bx in 0 until INPUT_W step 32) {
                checkpoint()
                for (y in by until min(by + 16, INPUT_H)) {
                    val y0 = ys.lower[y]
                    val y1 = min(y0 + 1, page.height - 1)
                    val dy = ys.fraction[y]
                    for (x in bx until min(bx + 32, INPUT_W)) {
                        val x0 = xs.lower[x]
                        val x1 = min(x0 + 1, page.width - 1)
                        val dx = xs.fraction[x]
                        val a = page.argb(x0, y0)
                        val b = page.argb(x1, y0)
                        val c = page.argb(x0, y1)
                        val d = page.argb(x1, y1)
                        val k = y * INPUT_W + x
                        dst.put(k, bilerpChannel(a, b, c, d, dx, dy, 0) / 255f)
                        dst.put(plane + k, bilerpChannel(a, b, c, d, dx, dy, 8) / 255f)
                        dst.put(2 * plane + k, bilerpChannel(a, b, c, d, dx, dy, 16) / 255f)
                    }
                }
            }
        }
        dst.rewind()
    }

    /** Product gate separate from the reference sampler. Thresholds are initial calibration values. */
    fun gridIssue(grid: FloatArray): String? {
        if (grid.size != GRID_FLOATS || grid.any { !it.isFinite() }) return "non-finite-or-shape"
        // Las curvas fuertes de libros gruesos pueden salir de [-1,1] en los
        // márgenes exteriores. Solo se rechazan NaN/Inf (arriba), pliegues
        // Jacobianos reales y una cobertura inservible.
        for (y in 0 until GRID_H - 1) for (x in 0 until GRID_W - 1) {
            val a = y * GRID_W + x
            val b = a + 1
            val c = a + GRID_W
            val d = c + 1
            // At each corner of a bilinear cell the two tangent vectors must preserve orientation.
            if (det(grid, a,b, a,c) <= 0f || det(grid, a,b, b,d) <= 0f ||
                det(grid, c,d, a,c) <= 0f || det(grid, c,d, b,d) <= 0f) return "fold-or-collapse"
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until NODES) {
            minX = minOf(minX,grid[i]); maxX = maxOf(maxX,grid[i])
            minY = minOf(minY,grid[NODES+i]); maxY = maxOf(maxY,grid[NODES+i])
        }
        if (maxX-minX < 0.4f || maxY-minY < 0.4f) return "low-coverage"
        return null
    }
    private fun det(g: FloatArray, p: Int, q: Int, r: Int, s: Int): Float =
        (g[q] - g[p]) * (g[NODES+s] - g[NODES+r]) -
        (g[NODES+q] - g[NODES+p]) * (g[s] - g[r])

    /** Sink consumes the first width*count pixels synchronously. Its array is reused. */
    fun render(
        page: BookPixels, grid: FloatArray, width: Int = page.width, height: Int = page.height,
        bandHeight: Int = 32, checkpoint: () -> Unit = {},
        sink: (firstY: Int, count: Int, argb: IntArray) -> Unit
    ) {
        require(grid.size == GRID_FLOATS && grid.all { it.isFinite() })
        require(page.width > 0 && page.height > 0 && width in 1..32768 && height in 1..32768)
        require(bandHeight in 1..128)
        val bands = min(bandHeight, height)
        val strip = IntArray(width * bands)
        val gx = AxisPlan(width, GRID_W)
        val xRows = FloatArray(bands * GRID_W)
        val yRows = FloatArray(bands * GRID_W)
        for (first in 0 until height step bands) {
            val lines = min(bands, height - first)
            for (r in 0 until lines) {
                val v = aligned(first + r, height, GRID_H)
                val j = v.toInt()
                val top = j * GRID_W
                val bottom = min(j + 1, GRID_H - 1) * GRID_W
                for (i in 0 until GRID_W) {
                    val k = r * GRID_W + i
                    xRows[k] = mix(grid[top+i], grid[bottom+i], v-j)
                    yRows[k] = mix(grid[NODES+top+i], grid[NODES+bottom+i], v-j)
                }
            }
            for (block in 0 until width step 128) {
                checkpoint()
                val end = min(block + 128, width)
                for (r in 0 until lines) {
                    val start = r * GRID_W
                    for (x in block until end) {
                        val i = gx.lower[x]
                        val j = min(i + 1, GRID_W - 1)
                        val dx = gx.fraction[x]
                        val sx = pixel(mix(xRows[start+i], xRows[start+j], dx), page.width)
                        val sy = pixel(mix(yRows[start+i], yRows[start+j], dx), page.height)
                        strip[r * width + x] = sampleOpaque(page, sx, sy)
                    }
                }
            }
            sink(first, lines, strip)
        }
    }

    private fun sampleOpaque(page: BookPixels, sx: Float, sy: Float): Int {
        val x0 = sx.toInt()
        val y0 = sy.toInt()
        val x1 = min(x0 + 1, page.width - 1)
        val y1 = min(y0 + 1, page.height - 1)
        val a = page.argb(x0,y0)
        val b = page.argb(x1,y0)
        val c = page.argb(x0,y1)
        val d = page.argb(x1,y1)
        val u = sx - x0
        val v = sy - y0
        val red = (bilerpChannel(a,b,c,d,u,v,16) + 0.5f).toInt()
        val green = (bilerpChannel(a,b,c,d,u,v,8) + 0.5f).toInt()
        val blue = (bilerpChannel(a,b,c,d,u,v,0) + 0.5f).toInt()
        return (255 shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
