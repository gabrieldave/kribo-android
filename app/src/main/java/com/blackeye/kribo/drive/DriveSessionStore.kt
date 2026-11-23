package com.blackeye.kribo.drive

import com.blackeye.kribo.security.SecureKeyValueStore

data class DriveSession(
    val email: String? = null,
    val accessToken: String? = null,
    val tokenExpiry: Long = 0L,
    val rootFolderId: String? = null,
    val lastSyncAt: Long = 0L,
    val lastNotes: Int = 0,
    val lastAttachments: Int = 0,
) {
    val connected: Boolean get() = !email.isNullOrBlank() || !accessToken.isNullOrBlank()
}

/** Encrypted session metadata; token never leaves AndroidKeyStore-backed storage. */
class DriveSessionStore(private val store: SecureKeyValueStore) {

    fun load(): DriveSession = DriveSession(
        email = store.get(KEY_EMAIL),
        accessToken = store.get(KEY_TOKEN),
        tokenExpiry = store.get(KEY_EXPIRY)?.toLongOrNull() ?: 0L,
        rootFolderId = store.get(KEY_ROOT),
        lastSyncAt = store.get(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L,
        lastNotes = store.get(KEY_NOTES)?.toIntOrNull() ?: 0,
        lastAttachments = store.get(KEY_ATTACHMENTS)?.toIntOrNull() ?: 0,
    )

    fun saveAuth(email: String?, token: String, expiry: Long) {
        email?.let { store.put(KEY_EMAIL, it) }
        store.put(KEY_TOKEN, token)
        store.put(KEY_EXPIRY, expiry.toString())
    }

    fun saveRootFolder(id: String) {
        store.put(KEY_ROOT, id)
    }

    fun saveLastSync(at: Long, notes: Int, attachments: Int) {
        store.put(KEY_LAST_SYNC, at.toString())
        store.put(KEY_NOTES, notes.toString())
        store.put(KEY_ATTACHMENTS, attachments.toString())
    }

    fun clear() {
        listOf(KEY_EMAIL, KEY_TOKEN, KEY_EXPIRY, KEY_ROOT, KEY_LAST_SYNC, KEY_NOTES, KEY_ATTACHMENTS)
            .forEach(store::remove)
    }

    private companion object {
        const val KEY_EMAIL = "drive_email_v1"
        const val KEY_TOKEN = "drive_token_v1"
        const val KEY_EXPIRY = "drive_token_expiry_v1"
        const val KEY_ROOT = "drive_root_v1"
        const val KEY_LAST_SYNC = "drive_last_sync_v1"
        const val KEY_NOTES = "drive_notes_v1"
        const val KEY_ATTACHMENTS = "drive_attachments_v1"
    }
}
