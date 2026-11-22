package com.blackeye.kribo.ui.scan

/**
 * Lazy seam for the ScanOffline OCR engine.
 *
 * The engine is intentionally not initialized during app start: it must only be loaded on the
 * first user-initiated scan so cold start stays inside the splash budget (<= 350 ms).
 *
 * TODO(scan-offline): bind the bundled engine here and warm it on Dispatchers.IO.
 */
object ScanEngine {

    @Volatile
    private var loaded: Boolean = false

    val isLoaded: Boolean
        get() = loaded

    /** Returns true when the engine is already in memory. Never blocks the caller. */
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        // Placeholder until ScanOffline is wired: no native library, no OCR model, no cost.
        return false
    }
}
