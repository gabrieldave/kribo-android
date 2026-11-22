package com.blackeye.kribo.ocr

import java.io.File

/** Minimal contract so the OCR engine can be swapped or faked in tests. */
interface OcrEngine {
    suspend fun recognize(file: File): String?
    fun close() {}
}
