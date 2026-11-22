package com.blackeye.kribo.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Layer 1 of the BLACK EYE Android security protocol: release APK signature pinning.
 *
 * The check is only enforced in release builds (see [com.blackeye.kribo.KriboApplication]).
 * Debug builds are intentionally allowed so developers can iterate locally.
 */
object AppSignatureVerifier {

    private const val TAG = "KriboSignature"

    /** BLACK EYE master release certificate (SHA-256). */
    const val MASTER_SHA256 =
        "64:6D:54:D5:7F:E2:44:6D:50:AF:43:BF:93:57:2F:9B:3A:33:20:C6:50:D5:B4:B4:82:F6:FB:C3:43:8C:3B:CB"

    /*
     * Play App Signing certificates.
     *
     * Fill these in after the first .aab is uploaded to the Internal Testing track, using the
     * fingerprints shown in Play Console > Setup > App signing. Do not remove the constants:
     * they document the rotation contract and keep the slot visible in review.
     *
     * const val PLAY_APP_SIGNING_SHA256_CLASSIC_ACTIVE = "<classic active SHA-256>"
     * const val PLAY_APP_SIGNING_SHA256_POST_QUANTUM_ML_DSA_65 = "<ML-DSA-65 SHA-256>"
     * const val PLAY_APP_SIGNING_SHA256_CLASSIC_PREVIOUS = "<previous classic SHA-256>"
     */

    /**
     * Fingerprints injected at Play release time. Keep empty until Play Console returns them:
     * an incorrect fingerprint here would brick the release build.
     */
    private val playAppSigningFingerprints: List<String> = listOf(
        // PLAY_APP_SIGNING_SHA256_CLASSIC_ACTIVE,
        // PLAY_APP_SIGNING_SHA256_POST_QUANTUM_ML_DSA_65,
        // PLAY_APP_SIGNING_SHA256_CLASSIC_PREVIOUS,
    )

    val trustedFingerprints: Set<String>
        get() = (playAppSigningFingerprints + MASTER_SHA256)
            .map(::normalizeFingerprint)
            .filter { it.isNotEmpty() }
            .toSet()

    /** Normalizes any fingerprint format (lowercase, missing colons, spaces) to AA:BB:… form. */
    fun normalizeFingerprint(raw: String): String {
        val hex = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.uppercase()
        if (hex.isEmpty() || hex.length % 2 != 0) return ""
        return hex.chunked(2).joinToString(":")
    }

    /**
     * Verifies the signer set returned by the platform.
     *
     * 1. Hybrid co-signing ([android.content.pm.SigningInfo.getApkContentsSigners]): every current
     *    signer must be trusted. A partially trusted set is rejected.
     * 2. Rotation fallback ([android.content.pm.SigningInfo.getSigningCertificateHistory]): when
     *    no current signer is recognized, accept only if Android reports a trusted predecessor.
     *    This lets a rotated build install without trusting an unknown current key.
     */
    fun isSignatureTrusted(
        currentSigners: List<String>,
        certificateHistory: List<String>,
        trusted: Set<String> = trustedFingerprints,
    ): Boolean {
        val current = currentSigners.map(::normalizeFingerprint).filter { it.isNotEmpty() }
        if (current.isEmpty()) return false

        val trustedSet = trusted.map(::normalizeFingerprint).filter { it.isNotEmpty() }.toSet()

        if (current.all { it in trustedSet }) return true
        if (current.any { it in trustedSet }) return false

        val history = certificateHistory.map(::normalizeFingerprint).filter { it.isNotEmpty() }
        return history.any { it in trustedSet }
    }

    fun hasTrustedSigner(
        context: Context,
        trusted: Set<String> = trustedFingerprints,
    ): Boolean {
        val packageManager = context.packageManager
        val packageName = context.packageName
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return false
                val current = signingInfo.apkContentsSigners.orEmpty().mapNotNull { fingerprintOf(it.toByteArray()) }
                val history =
                    signingInfo.signingCertificateHistory.orEmpty().mapNotNull { fingerprintOf(it.toByteArray()) }
                isSignatureTrusted(current, history, trusted)
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val current = info.signatures.orEmpty().mapNotNull { fingerprintOf(it.toByteArray()) }
                isSignatureTrusted(current, emptyList(), trusted)
            }
        } catch (exception: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found while verifying signature", exception)
            false
        }
    }

    /** Terminates the process when the installed APK is not signed by a trusted certificate. */
    fun enforce(context: Context) {
        if (hasTrustedSigner(context)) return
        Log.e(TAG, "Signature mismatch: this APK is not signed by a BLACK EYE trusted certificate.")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun fingerprintOf(certificateBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificateBytes)
        return digest.joinToString(separator = ":") { byte ->
            (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }
    }
}
