package com.blackeye.kribo.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Almacén local cifrado con AndroidKeyStore (AES-256-GCM), SIN
 * `androidx.security:security-crypto`.
 *
 * Cada valor se guarda como Base64(IV || ciphertext) en SharedPreferences
 * privadas. La llave nunca sale del KeyStore del dispositivo.
 */
interface SecureKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Implementación de tests: sin AndroidKeyStore, pero misma interfaz. */
class InMemorySecureStore : SecureKeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) {
        values[key] = value
    }
    override fun remove(key: String) {
        values.remove(key)
    }
}

class KeystoreSecureStore(context: Context) : SecureKeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? = runCatching {
        val stored = prefs.getString(key, null) ?: return@runCatching null
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        if (raw.size <= IV_SIZE) return@runCatching null
        val iv = raw.copyOfRange(0, IV_SIZE)
        val ciphertext = raw.copyOfRange(IV_SIZE, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    override fun put(key: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + ciphertext
            prefs.edit()
                .putString(key, Base64.encodeToString(payload, Base64.NO_WRAP))
                .apply()
        }
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "kribo_pro_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "kribo_secure"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
