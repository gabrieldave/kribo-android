package com.blackeye.kribo.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OCR principal: PaddleOCR PP-OCRv5 (RapidAI/RapidOCR v3.10.0, Apache-2.0)
 * ejecutado en ONNX Runtime, completamente offline.
 *
 * Flujo:
 *  1. Imagen a 1600 px de lado largo, BGR, normalizada (media 0.5, desvío 0.5).
 *  2. DBNet mobile: umbral 0.3 y expansión de caja (unclip) 1.55.
 *  3. Recorte por renglón, alto 48 px, ancho máximo 3200, relleno gris 127.
 *  4. CTC de 504 clases con el diccionario latino incrustado.
 *  5. [OcrLayoutSorter] ordena los renglones antes de unirlos.
 *
 * Si faltan los modelos, la firma SHA-256 no coincide o ONNX falla, el motor
 * devuelve null: no hay respaldo pesado ni se carga nada en el arranque.
 */
class PaddleOcrEngine(
    context: Context,
) : OcrEngine {

    private val appContext = context.applicationContext
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val lock = Any()
    private var sessions: Sessions? = null
    private var onnxFailed = false
    private val characters: List<String> by lazy { loadDictionary() }

    private data class Sessions(
        val det: OrtSession,
        val rec: OrtSession,
        val detInput: String,
        val recInput: String,
    )

    override suspend fun recognize(file: File): String? = withContext(Dispatchers.Default) {
        if (!file.isFile) return@withContext null
        val ready = ensureSessions() ?: return@withContext null

        runCatching { recognizeOnnx(file, ready) }
            .getOrNull()
            ?.let(OcrTextSanitizer::cleanForOcr)
            ?.takeIf { it.isNotBlank() }
    }

    override fun close() {
        synchronized(lock) {
            sessions?.let {
                runCatching { it.det.close() }
                runCatching { it.rec.close() }
            }
            sessions = null
        }
    }

    private fun ensureSessions(): Sessions? = synchronized(lock) {
        sessions?.let { return it }
        if (onnxFailed) return null
        runCatching {
            val detBytes = readVerified(DET_ASSET, DET_SHA256)
            val recBytes = readVerified(REC_ASSET, REC_SHA256)
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val det = env.createSession(detBytes, options)
            val rec = env.createSession(recBytes, options)
            Sessions(
                det = det,
                rec = rec,
                detInput = det.inputNames.first(),
                recInput = rec.inputNames.first(),
            ).also { sessions = it }
        }.getOrElse {
            onnxFailed = true
            null
        }
    }

    private fun recognizeOnnx(file: File, ready: Sessions): String? {
        val source = decodeOriented(file, DET_LONG_SIDE) ?: return null
        try {
            val detBitmap = scaleToDetection(source)
            val scaleX = source.width.toFloat() / detBitmap.width
            val scaleY = source.height.toFloat() / detBitmap.height
            val boxes = detect(detBitmap, ready)
            if (detBitmap !== source) detBitmap.recycle()
            if (boxes.isEmpty()) return null
            val lines = ArrayList<OcrLayoutSorter.Box>(boxes.size)
            for (box in boxes) {
                val left = (box.left * scaleX).roundToInt().coerceIn(0, source.width - 1)
                val top = (box.top * scaleY).roundToInt().coerceIn(0, source.height - 1)
                val right = (box.right * scaleX).roundToInt().coerceIn(left + 1, source.width)
                val bottom = (box.bottom * scaleY).roundToInt().coerceIn(top + 1, source.height)
                val text = recognizeLine(source, left, top, right - left, bottom - top, ready)
                if (text.isNotBlank()) {
                    lines.add(OcrLayoutSorter.Box(left, top, right, bottom, text))
                }
            }
            val ordered = OcrLayoutSorter.sort(lines)
            return ordered.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
        } finally {
            source.recycle()
        }
    }

    private fun detect(bitmap: Bitmap, ready: Sessions): List<OcrLayoutSorter.Box> {
        val width = bitmap.width
        val height = bitmap.height
        val input = FloatArray(3 * width * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val plane = width * height
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // ONNX espera BGR.
            input[i] = b / 127.5f - 1f
            input[plane + i] = g / 127.5f - 1f
            input[2 * plane + i] = r / 127.5f - 1f
        }

        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            ready.det.run(mapOf(ready.detInput to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val probability = result.get(0).value as Array<Array<Array<FloatArray>>>
                val map = probability[0][0]
                return boxesFromProbability(map, DET_THRESHOLD, UNCLIP_RATIO)
            }
        }
    }

    private fun boxesFromProbability(
        probability: Array<FloatArray>,
        threshold: Float,
        unclipRatio: Float,
    ): List<OcrLayoutSorter.Box> {
        val height = probability.size
        if (height == 0) return emptyList()
        val width = probability[0].size
        if (width == 0) return emptyList()

        val rowInk = IntArray(height)
        for (y in 0 until height) {
            var count = 0
            val row = probability[y]
            for (x in 0 until width) if (row[x] >= threshold) count++
            rowInk[y] = count
        }
        val minRowInk = max(2, (width * 0.002f).roundToInt())
        val rowBands = bands(rowInk, minRowInk, max(1, height / 400))
        if (rowBands.isEmpty()) return emptyList()

        val boxes = ArrayList<OcrLayoutSorter.Box>()
        for ((top, bottom) in rowBands) {
            val columnInk = IntArray(width)
            for (y in top until bottom) {
                val row = probability[y]
                for (x in 0 until width) if (row[x] >= threshold) columnInk[x]++
            }
            val minColInk = max(1, ((bottom - top) * 0.02f).roundToInt())
            val colBands = bands(columnInk, minColInk, max(2, width / 90))
            for ((left, right) in colBands) {
                if (right - left < 4 || bottom - top < 4) continue
                val expanded = unclip(left, top, right, bottom, width, height, unclipRatio)
                boxes.add(expanded)
            }
        }
        return boxes
    }

    /** Bandas contiguas de índices activos, tolerando huecos pequeños. */
    private fun bands(counts: IntArray, minimum: Int, maxGap: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var start = -1
        var lastActive = -1
        for (i in counts.indices) {
            if (counts[i] >= minimum) {
                if (start < 0) {
                    start = i
                } else if (i - lastActive > maxGap + 1) {
                    out.add(start to lastActive + 1)
                    start = i
                }
                lastActive = i
            }
        }
        if (start >= 0) out.add(start to lastActive + 1)
        return out
    }

    private fun unclip(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        width: Int,
        height: Int,
        ratio: Float,
    ): OcrLayoutSorter.Box {
        val boxWidth = (right - left).coerceAtLeast(1)
        val boxHeight = (bottom - top).coerceAtLeast(1)
        val perimeter = 2f * (boxWidth + boxHeight)
        val distance = (boxWidth * boxHeight * ratio / perimeter)
            .coerceAtMost(boxHeight * 0.45f)
        val d = distance.roundToInt().coerceAtLeast(1)
        return OcrLayoutSorter.Box(
            left = (left - d).coerceAtLeast(0),
            top = (top - d).coerceAtLeast(0),
            right = (right + d).coerceAtMost(width),
            bottom = (bottom + d).coerceAtMost(height),
            text = "",
        )
    }

    private fun recognizeLine(
        source: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        ready: Sessions,
    ): String {
        if (width < 4 || height < 4) return ""
        val crop = runCatching { Bitmap.createBitmap(source, left, top, width, height) }
            .getOrNull() ?: return ""
        val recWidth = ((width * REC_HEIGHT.toFloat() / height).roundToInt())
            .coerceIn(16, REC_MAX_WIDTH)
        val expected = ((recWidth + 7) / 8) * 8
        val scaled = Bitmap.createScaledBitmap(crop, recWidth, REC_HEIGHT, true)
        if (scaled !== crop) crop.recycle()
        try {
            val input = FloatArray(3 * REC_HEIGHT * expected)
            val pixels = IntArray(recWidth * REC_HEIGHT)
            scaled.getPixels(pixels, 0, recWidth, 0, 0, recWidth, REC_HEIGHT)
            val plane = REC_HEIGHT * expected
            val grayValue = 127f / 127.5f - 1f
            for (y in 0 until REC_HEIGHT) {
                for (x in 0 until expected) {
                    val index = y * expected + x
                    val position = y * recWidth + x
                    if (x < recWidth) {
                        val color = pixels[position]
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF
                        input[index] = b / 127.5f - 1f
                        input[plane + index] = g / 127.5f - 1f
                        input[2 * plane + index] = r / 127.5f - 1f
                    } else {
                        input[index] = grayValue
                        input[plane + index] = grayValue
                        input[2 * plane + index] = grayValue
                    }
                }
            }
            val shape = longArrayOf(1, 3, REC_HEIGHT.toLong(), expected.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
                ready.rec.run(mapOf(ready.recInput to tensor)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val batch = result.get(0).value as Array<Array<FloatArray>>
                    val logits = batch.firstOrNull() ?: return ""
                    val classes = logits.firstOrNull()?.size ?: 0
                    if (classes <= 0) return ""
                    val indices = CtcDecoder.argmax(logits)
                    return CtcDecoder.decode(indices, characters).trim()
                }
            }
        } finally {
            scaled.recycle()
        }
    }

    private fun scaleToDetection(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= DET_LONG_SIDE && source.width % 32 == 0 && source.height % 32 == 0) {
            return source
        }
        val ratio = DET_LONG_SIDE.toFloat() / longest.toFloat()
        val targetWidth = ((source.width * ratio).roundToInt()).coerceAtLeast(32)
        val targetHeight = ((source.height * ratio).roundToInt()).coerceAtLeast(32)
        val roundedWidth = ((targetWidth + 31) / 32) * 32
        val roundedHeight = ((targetHeight + 31) / 32) * 32
        return Bitmap.createScaledBitmap(source, roundedWidth, roundedHeight, true)
    }

    private fun decodeOriented(file: File, maxLongSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxLongSide) sample *= 2
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
        val scaled = scaleDown(bitmap, maxLongSide)
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

    private fun loadDictionary(): List<String> {
        val raw = readVerified(DICT_ASSET, DICT_SHA256)
            .toString(Charsets.UTF_8)
        return raw.split('\n')
            .map { it.removeSuffix("\r") }
            .filter { it.isNotEmpty() }
    }

    private fun readVerified(asset: String, expectedSha256: String): ByteArray {
        val bytes = appContext.assets.open(asset).use { it.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(actual.equals(expectedSha256, ignoreCase = true)) {
            "SHA-256 de $asset no coincide"
        }
        return bytes
    }

    companion object {
        const val DET_ASSET = "models/ocr/ch_PP-OCRv5_det_mobile.onnx"
        const val REC_ASSET = "models/ocr/latin_PP-OCRv5_rec_mobile.onnx"
        const val DICT_ASSET = "models/ocr/latin_ppocrv5_dict.txt"

        // RapidAI/RapidOCR v3.10.0 · PP-OCRv5 mobile + diccionario latino.
        const val DET_SHA256 = "4d97c44a20d30a81aad087d6a396b08f786c4635742afc391f6621f5c6ae78ae"
        const val REC_SHA256 = "b20bd37c168a570f583afbc8cd7925603890efbcdc000a59e22c269d160b5f5a"
        const val DICT_SHA256 = "3c0a8a79b612653c25f765271714f71281e4e955962c153e272b7b8c1d2b13ff"

        const val DET_LONG_SIDE = 1600
        const val DET_THRESHOLD = 0.3f
        const val UNCLIP_RATIO = 1.55f
        const val REC_HEIGHT = 48
        const val REC_MAX_WIDTH = 3200
    }
}
