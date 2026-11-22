package com.blackeye.kribo.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    fun observeNotes(): Flow<List<Note>> = dao.observeAll()

    suspend fun getNote(id: Long): Note? = dao.getById(id)

    suspend fun insertNote(content: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(Note(content = content, createdAt = now, updatedAt = now))
    }

    suspend fun updateNote(id: Long, content: String, createdAt: Long) {
        dao.update(
            Note(
                id = id,
                content = content,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteNote(id: Long) = dao.deleteById(id)
}
