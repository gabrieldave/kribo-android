package com.blackeye.kribo.ocr

import com.blackeye.kribo.security.InMemorySecureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrQuotaStoreTest {

    @Test
    fun freeTierAllowsFiveScansThenBlocks() {
        val store = InMemorySecureStore()
        val quota = OcrQuotaStore(store)

        assertEquals(5, quota.remaining(isPro = false))
        repeat(OcrQuotaStore.FREE_OCR_LIMIT) {
            assertTrue(quota.canScan(isPro = false))
            quota.consume()
        }

        assertFalse(quota.canScan(isPro = false))
        assertEquals(0, quota.remaining(isPro = false))
    }

    @Test
    fun proUserBypassesTheLimit() {
        val store = InMemorySecureStore()
        val quota = OcrQuotaStore(store)
        repeat(10) { quota.consume() }

        assertTrue(quota.canScan(isPro = true))
        assertEquals(Int.MAX_VALUE, quota.remaining(isPro = true))
        assertFalse(quota.canScan(isPro = false))
    }

    @Test
    fun counterSurvivesNewStoreInstanceAndCanBeReset() {
        val backing = InMemorySecureStore()
        OcrQuotaStore(backing).consume()
        OcrQuotaStore(backing).consume()

        val reloaded = OcrQuotaStore(backing)
        assertEquals(2, reloaded.used())
        assertEquals(3, reloaded.remaining(isPro = false))

        reloaded.reset()
        assertEquals(0, reloaded.used())
        assertEquals(5, reloaded.remaining(isPro = false))
    }

    @Test
    fun corruptedValueFallsBackToZero() {
        val store = InMemorySecureStore()
        store.put("ocr_scans_used_v1", "not-a-number")
        assertEquals(0, OcrQuotaStore(store).used())
    }
}
