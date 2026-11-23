package com.blackeye.kribo.scan

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pixel transfer functions. Estimation of confidence, paper and protection masks is upstream. */
object PaperOps {
    private val linear = FloatArray(256) {
        val s = it/255f
        if (s <= 0.04045f) s/12.92f else ((s+0.055f)/1.055f).pow(2.4f)
    }
    private val srgb = IntArray(4097) {
        val v = it/4096f
        val s = if (v <= 0.0031308f) 12.92f*v else 1.055f*v.pow(1f/2.4f)-0.055f
        (255f*s).roundToInt().coerceIn(0,255)
    }
    private fun code(v: Float) = srgb[(v.coerceIn(0f,1f)*4096).roundToInt()]
    private fun smooth(lo: Float, hi: Float, x: Float): Float {
        val s = ((x-lo)/(hi-lo)).coerceIn(0f,1f)
        return s*s*(3f-2f*s)
    }
    /** Linear Y background and target in [0,1]. protected=true returns original bytes exactly. */
    fun liftShadow(p: Int, paper: Float, target: Float, support: Float, protected: Boolean): Int {
        if (protected || support <= 0f || paper < 0.025f || target <= paper) return p
        val r = linear[p ushr 16 and 255]
        val g = linear[p ushr 8 and 255]
        val b = linear[p and 255]
        val lum = 0.2126f*r + 0.7152f*g + 0.0722f*b
        val w = support.coerceIn(0f,1f)*smooth(0.20f,0.65f,lum/paper)
        val gain = 1f+w*((target/paper).coerceIn(1f,1.8f)-1f)
        val safe = min(gain,1f/max(1e-6f,maxOf(r,g,b)))
        if (safe <= 1f) return p
        return (255 shl 24) or (code(r*safe) shl 16) or (code(g*safe) shl 8) or code(b*safe)
    }

    /** Blend in linear light. donor is an opaque sRGB sample reconstructed from local paper. */
    fun fillFinger(p: Int, donor: Int, alpha: Float, protected: Boolean): Int {
        if (protected || alpha <= 0f) return p
        if (alpha >= 1f) return donor
        fun ch(shift: Int): Int {
            val a = linear[p ushr shift and 255]
            val b = linear[donor ushr shift and 255]
            return code(a + (b-a)*alpha)
        }
        return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
