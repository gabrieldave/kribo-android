package com.blackeye.kribo.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest
import java.util.Locale

/**
 * Layer 1 of the BLACK EYE Android security protocol: release APK signature pinning.
 *
 * - Only enforced in Release (`BuildConfig.DEBUG` short-circuits in KriboApplication).
 * - Uses GET_SIGNING_CERTIFICATES and validates every current signer (hybrid/co-signing).
 * - Falls back to signingCertificateHistory only when apkContentsSigners is empty.
 * - On mismatch the app shows a localized blocking screen; it never crashes.
 */
object AppSignatureVerifier {

    private const val TAG = "KriboSignature"

    /** BLACK EYE master upload/release certificate (normalized SHA-256, no colons). */
    const val MASTER_SHA256 =
        "646D54D57FE2446D50AF43BF93572F9B3A3320C650D5B4B482F6FBC3438C3BCB"

    // Google Play App Signing — official fingerprints for kribo-android v1.0.10+.
    const val PLAY_APP_SIGNING_SHA256_1 =
        "0B3BB3C5DA56FB10431EF28C056780CCA147E65DF7294FCB45EECA30C29AC2D1"
    const val PLAY_APP_SIGNING_SHA256_2 =
        "9F4AECDB28F14BCCC4FE8B5084644B755909DD97CFCC662DB3BA7731DD5C9FF1"
    const val PLAY_APP_SIGNING_SHA256_3 =
        "CCF5AE4289722C1D95D536A277C336A7ECD52B7D16C9E6075A1C2381A1E4CD7C"

    /** Exactly four official keys: BLACK EYE master/upload + the 3 Play signing keys. */
    val ALLOWED_SHA256_FINGERPRINTS: Set<String> = setOf(
        MASTER_SHA256,
        PLAY_APP_SIGNING_SHA256_1,
        PLAY_APP_SIGNING_SHA256_2,
        PLAY_APP_SIGNING_SHA256_3,
    ).map(::normalizeFingerprint)
        .filter { it.isNotEmpty() }
        .toSet()

    val trustedFingerprints: Set<String>
        get() = ALLOWED_SHA256_FINGERPRINTS

    /** Uppercase SHA-256 hex, no colons. Invalid values normalize to "". */
    fun normalizeFingerprint(raw: String): String {
        val hex = raw
            .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            .uppercase(Locale.US)
        if (hex.length != 64) return ""
        return hex
    }

    /**
     * Hybrid co-signing: every current signer must be trusted.
     * If apkContentsSigners is empty, the signing certificate history is the fallback.
     */
    fun isSignatureTrusted(
        currentSigners: List<String>,
        certificateHistory: List<String>,
        trusted: Set<String> = trustedFingerprints,
    ): Boolean {
        val trustedSet = trusted.map(::normalizeFingerprint).filter { it.isNotEmpty() }.toSet()
        if (trustedSet.isEmpty()) return false

        val current = currentSigners.map(::normalizeFingerprint).filter { it.isNotEmpty() }
        if (current.isNotEmpty()) return current.all { it in trustedSet }

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
                val current = signingInfo.apkContentsSigners.orEmpty()
                    .mapNotNull { fingerprintOf(it.toByteArray()) }
                val history = signingInfo.signingCertificateHistory.orEmpty()
                    .mapNotNull { fingerprintOf(it.toByteArray()) }
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

    /** Returns true only for a trusted signature. Callers show the blocking screen on false. */
    fun enforce(context: Context): Boolean = hasTrustedSigner(context)

    private fun fingerprintOf(certificateBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificateBytes)
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).uppercase(Locale.US).padStart(2, '0')
        }
    }
}
