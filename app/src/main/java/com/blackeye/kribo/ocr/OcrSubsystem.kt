package com.blackeye.kribo.ocr

import android.content.Context
import java.io.File

/**
 * Lazy owner of the PP-OCRv5 ONNX engine.
 *
 * NOTHING here runs at app start. The ONNX environment, models (12 MB) and dictionary are only
 * touched on the first [recognize] call, which always happens off the main thread.
 */
class OcrSubsystem(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var engine: PaddleOcrEngine? = null

    val isLoaded: Boolean
        get() = engine != null

    suspend fun recognize(file: File): String? = ensureEngine().recognize(file)

    fun close() {
        synchronized(this) {
            engine?.close()
            engine = null
        }
    }

    private fun ensureEngine(): PaddleOcrEngine = synchronized(this) {
        engine ?: PaddleOcrEngine(appContext).also { engine = it }
    }
}
