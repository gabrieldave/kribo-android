package com.blackeye.kribo.drive

import com.blackeye.kribo.security.SecureKeyValueStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persisted note tombstones so a delete on one device never resurrects from Drive. */
class DriveTombstoneStore(private val store: SecureKeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }

    fun all(): Map<Long, Long> = runCatching {
        val raw = store.get(KEY) ?: return@runCatching emptyMap()
        json.decodeFromString<Map<String, Long>>(raw)
            .mapNotNull { (key, value) -> key.toLongOrNull()?.let { it to value } }
            .toMap()
    }.getOrDefault(emptyMap())

    fun mark(noteId: Long, deletedAt: Long) {
        val current = all().toMutableMap()
        current[noteId] = maxOf(current[noteId] ?: 0L, deletedAt)
        persist(current)
    }

    fun clear(noteId: Long) {
        val current = all().toMutableMap()
        if (current.remove(noteId) != null) persist(current)
    }

    fun merge(remote: Map<Long, Long>) {
        if (remote.isEmpty()) return
        val current = all().toMutableMap()
        remote.forEach { (id, at) -> current[id] = maxOf(current[id] ?: 0L, at) }
        persist(current)
    }

    /** Folder IDs present after the last completed sync; lets sync detect remote deletions. */
    fun syncedFolderIds(): Set<Long> = loadLongSet(KEY_SYNCED_FOLDERS)

    fun saveSyncedFolderIds(ids: Set<Long>) = saveLongSet(KEY_SYNCED_FOLDERS, ids)

    /** Folders deleted locally since the last sync so they are not resurrected on pull. */
    fun deletedFolderIds(): Set<Long> = loadLongSet(KEY_DELETED_FOLDERS)

    fun markFolderDeleted(folderId: Long) {
        val current = deletedFolderIds().toMutableSet()
        if (current.add(folderId)) saveLongSet(KEY_DELETED_FOLDERS, current)
    }

    fun clearDeletedFolders() {
        store.remove(KEY_DELETED_FOLDERS)
    }

    private fun loadLongSet(key: String): Set<Long> = runCatching {
        val raw = store.get(key) ?: return@runCatching emptySet()
        json.decodeFromString<List<Long>>(raw).toSet()
    }.getOrDefault(emptySet())

    private fun saveLongSet(key: String, values: Set<Long>) {
        store.put(key, json.encodeToString(values.sorted()))
    }

    private fun persist(values: Map<Long, Long>) {
        store.put(
            KEY,
            json.encodeToString(values.mapKeys { it.key.toString() }),
        )
    }

    private companion object {
        const val KEY = "drive_tombstones_v1"
        const val KEY_SYNCED_FOLDERS = "drive_synced_folders_v1"
        const val KEY_DELETED_FOLDERS = "drive_deleted_folders_v1"
    }
}
