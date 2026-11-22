package com.blackeye.kribo.ocr

import com.blackeye.kribo.security.SecureKeyValueStore

/**
 * Encrypted counter for the free OCR tier. The value lives in AndroidKeyStore-backed storage,
 * so clearing app data is the only way to reset it on a non-rooted device.
 */
class OcrQuotaStore(
    private val store: SecureKeyValueStore,
    private val limit: Int = FREE_OCR_LIMIT,
) {

    fun used(): Int = store.get(KEY_USED)?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun remaining(isPro: Boolean): Int =
        if (isPro) Int.MAX_VALUE else (limit - used()).coerceAtLeast(0)

    fun canScan(isPro: Boolean): Boolean = isPro || used() < limit

    /** Returns the new used count. Call only after a successful OCR pass. */
    fun consume(): Int {
        val next = used() + 1
        store.put(KEY_USED, next.toString())
        return next
    }

    fun reset() = store.remove(KEY_USED)

    companion object {
        const val FREE_OCR_LIMIT = 5
        private const val KEY_USED = "ocr_scans_used_v1"
    }
}
