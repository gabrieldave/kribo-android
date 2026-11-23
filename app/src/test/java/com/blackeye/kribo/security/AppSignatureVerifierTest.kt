package com.blackeye.kribo.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSignatureVerifierTest {

    private val master = AppSignatureVerifier.MASTER_SHA256
    private val unknown = fingerprint("AA")
    private val anotherUnknown = fingerprint("BB")

    private fun fingerprint(byte: String): String = List(32) { byte }.joinToString(":")

    @Test
    fun masterFingerprintIsCanonical() {
        assertEquals(master, AppSignatureVerifier.normalizeFingerprint(master))
        assertEquals(64, master.length)
        assertEquals("", AppSignatureVerifier.normalizeFingerprint("not-a-fingerprint"))
    }

    @Test
    fun normalizationAcceptsLowercaseAndMissingSeparators() {
        val raw = master.lowercase()
        assertEquals(master, AppSignatureVerifier.normalizeFingerprint(raw))
    }

    @Test
    fun normalizationRejectsMalformedInput() {
        assertEquals("", AppSignatureVerifier.normalizeFingerprint(""))
        assertEquals("", AppSignatureVerifier.normalizeFingerprint("not-a-fingerprint"))
        assertEquals("", AppSignatureVerifier.normalizeFingerprint("ABC"))
    }

    @Test
    fun emptySignerSetIsRejected() {
        assertFalse(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = emptyList(),
                certificateHistory = emptyList(),
            ),
        )
    }

    @Test
    fun trustedMasterSignerIsAccepted() {
        assertTrue(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = listOf(master),
                certificateHistory = emptyList(),
            ),
        )
    }

    @Test
    fun untrustedSignerIsRejected() {
        assertFalse(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = listOf(unknown),
                certificateHistory = emptyList(),
            ),
        )
    }

    @Test
    fun hybridCoSigningRequiresEveryCurrentSignerToBeTrusted() {
        assertTrue(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = listOf(master),
                certificateHistory = emptyList(),
            ),
        )
        assertFalse(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = listOf(master, unknown),
                certificateHistory = emptyList(),
            ),
        )
    }

    @Test
    fun hybridCoSigningAcceptsAllTrustedSigners() {
        val second = fingerprint("CC")
        assertTrue(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = listOf(master, second),
                certificateHistory = emptyList(),
                trusted = setOf(master, second),
            ),
        )
    }

    @Test
    fun emptyCurrentSignersFallBackToTrustedHistory() {
        assertTrue(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = emptyList(),
                certificateHistory = listOf(master),
            ),
        )
    }

    @Test
    fun unknownCurrentSignerDoesNotFallBackToHistory() {
        assertFalse(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = listOf(unknown),
                certificateHistory = listOf(master),
            ),
        )
    }

    @Test
    fun rotationHistoryWithoutTrustedCertificateIsRejected() {
        assertFalse(
            AppSignatureVerifier.isSignatureTrusted(
                currentSigners = listOf(unknown),
                certificateHistory = listOf(anotherUnknown),
            ),
        )
    }

    @Test
    fun masterFingerprintIsInTheTrustedSet() {
        assertTrue(
            AppSignatureVerifier.trustedFingerprints.contains(
                AppSignatureVerifier.normalizeFingerprint(master),
            ),
        )
    }
}
