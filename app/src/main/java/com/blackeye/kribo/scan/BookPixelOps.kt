package com.blackeye.kribo.scan

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.floor

/** Finger removal and spine-shadow compensation on an already flattened page. */
object BookPixelOps {

    fun removeFingers(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 16 || h < 16) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val bandX = (w * 0.22f).toInt().coerceAtLeast(8)
        val bandY = (h * 0.22f).toInt().coerceAtLeast(8)
        val skin = BooleanArray(w * h)
        val dark = BooleanArray(w * h)
        val candidate = BooleanArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val edge = x < bandX || x >= w - bandX || y >= h - bandY
                val p = pixels[row + x]
                val r = p shr 16 and 255
                val g = p shr 8 and 255
                val b = p and 255
                val ycc = 0.299f * r + 0.587f * g + 0.114f * b
                val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
                val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
                // Skin is classified everywhere so a finger entering from the
                // edge is removed as one connected region, not cut at 22 %.
                skin[row + x] = ycc > 55f && cr in 132f..185f && cb in 60f..135f &&
                    (cr - cb) >= 9f && r > b + 8
                dark[row + x] = edge && ycc < 95f && neighborsDark(pixels, w, h, x, y)
                candidate[row + x] = skin[row + x] || dark[row + x]
            }
        }
        val mask = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(index: Int) {
            if (!mask[index] && candidate[index]) {
                mask[index] = true
                queue[tail++] = index
            }
        }
        for (x in 0 until w) push(x)
        for (y in 0 until h) {
            push(y * w)
            push(y * w + w - 1)
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        var count = 0
        for (m in mask) if (m) count++
        if (count == 0) return source

        val paper = globalPaper(pixels, w, h)
        val alpha = FloatArray(w * h)
        for (i in mask.indices) if (mask[i]) alpha[i] = 1f
        val feather = boxBlur(alpha, w, h, 2)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val a = feather[i].coerceIn(0f, 1f)
                if (a <= 0.01f) {
                    out[i] = pixels[i]
                } else {
                    val donor = if (x < w / 2) pixels[y * w + minOf(w - 1, bandX + 6)] else pixels[y * w + maxOf(0, w - bandX - 7)]
                    val safeDonor = if (luminance(donor) > 110 && !isSkin(donor)) donor else paper
                    val protected = luminance(pixels[i]) < 70
                    out[i] = PaperOps.fillFinger(pixels[i], safeDonor, a, protected)
                }
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    fun liftShadows(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 16 || h < 16) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val cols = 16
        val rows = 20
        val cellW = (w + cols - 1) / cols
        val cellH = (h + rows - 1) / rows
        val background = FloatArray(cols * rows)
        for (cy in 0 until rows) for (cx in 0 until cols) {
            val x0 = cx * cellW
            val y0 = cy * cellH
            val hist = IntArray(256)
            var total = 0
            var y = y0
            while (y < minOf(h, y0 + cellH)) {
                var x = x0
                while (x < minOf(w, x0 + cellW)) {
                    hist[luminance(pixels[y * w + x])]++
                    total++
                    x += 3
                }
                y += 3
            }
            background[cy * cols + cx] = percentile(hist, total, 0.90f) / 255f
        }
        // Target: bright outer paper, not pure white.
        val outer = ArrayList<Float>()
        for (cy in 0 until rows) for (cx in 0 until cols) {
            if (cx < 3 || cx >= cols - 3 || cy < 3 || cy >= rows - 3) outer += background[cy * cols + cx]
        }
        outer.sort()
        val target = maxOf(outer.getOrElse(outer.size / 2) { 0.85f }, 0.94f).coerceIn(0.70f, 0.99f)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val gy = (y.toFloat() / h) * rows - 0.5f
            val y0 = floor(gy).toInt().coerceIn(0, rows - 1)
            val y1 = (y0 + 1).coerceAtMost(rows - 1)
            val ty = (gy - y0).coerceIn(0f, 1f)
            for (x in 0 until w) {
                val gx = (x.toFloat() / w) * cols - 0.5f
                val x0 = floor(gx).toInt().coerceIn(0, cols - 1)
                val x1 = (x0 + 1).coerceAtMost(cols - 1)
                val tx = (gx - x0).coerceIn(0f, 1f)
                val paper = lerp(
                    lerp(background[y0 * cols + x0], background[y0 * cols + x1], tx),
                    lerp(background[y1 * cols + x0], background[y1 * cols + x1], tx),
                    ty,
                ).coerceIn(0.025f, 1f)
                val p = pixels[y * w + x]
                val lum = luminance(p) / 255f
                val protected = lum < 0.12f || saturation(p) > 90
                out[y * w + x] = PaperOps.liftShadow(p, paper, target, 1f, protected)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun globalPaper(pixels: IntArray, w: Int, h: Int): Int {
        val reds = ArrayList<Int>(512)
        val greens = ArrayList<Int>(512)
        val blues = ArrayList<Int>(512)
        val step = 5
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val p = pixels[y * w + x]
                if (luminance(p) > 125 && !isSkin(p)) {
                    reds += p shr 16 and 255
                    greens += p shr 8 and 255
                    blues += p and 255
                }
            }
        }
        if (reds.isEmpty()) return 0xFFFFFFFF.toInt()
        reds.sort(); greens.sort(); blues.sort()
        val m = reds.size / 2
        return (0xFF shl 24) or (reds[m] shl 16) or (greens[m] shl 8) or blues[m]
    }

    private fun neighborsDark(pixels: IntArray, w: Int, h: Int, x: Int, y: Int): Boolean {
        var dark = 0
        var total = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            total++
            if (luminance(pixels[ny * w + nx]) < 95) dark++
        }
        return total > 0 && dark >= (total * 0.8f).toInt()
    }

    private fun isSkin(p: Int): Boolean {
        val r = p shr 16 and 255
        val g = p shr 8 and 255
        val b = p and 255
        val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
        val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
        return cr in 132f..185f && cb in 60f..135f && (cr - cb) >= 9f && r > b + 8
    }

    private fun saturation(p: Int): Int {
        val r = p shr 16 and 255
        val g = p shr 8 and 255
        val b = p and 255
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max == 0) 0 else (max - min) * 255 / max
    }

    private fun luminance(p: Int): Int {
        val r = p shr 16 and 255
        val g = p shr 8 and 255
        val b = p and 255
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun percentile(hist: IntArray, total: Int, fraction: Float): Int {
        if (total <= 0) return 255
        val wanted = (total * fraction).toInt().coerceAtLeast(1)
        var sum = 0
        for (i in hist.indices) {
            sum += hist[i]
            if (sum >= wanted) return i
        }
        return 255
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val d = radius * 2 + 1
        for (y in 0 until h) {
            var sum = 0f
            for (x in -radius..radius) sum += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = sum / d
                sum += src[y * w + (x + radius + 1).coerceAtMost(w - 1)] - src[y * w + (x - radius).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (y in -radius..radius) sum += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / d
                sum += tmp[(y + radius + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - radius).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
