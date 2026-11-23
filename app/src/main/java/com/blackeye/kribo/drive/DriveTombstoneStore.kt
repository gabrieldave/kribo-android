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

    private fun persist(values: Map<Long, Long>) {
        store.put(
            KEY,
            json.encodeToString(values.mapKeys { it.key.toString() }),
        )
    }

    private companion object {
        const val KEY = "drive_tombstones_v1"
    }
}
