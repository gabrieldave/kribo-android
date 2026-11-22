package com.blackeye.kribo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY id ASC")
    suspend fun getForNote(noteId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY id ASC")
    fun observeForNote(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}
