package com.blackeye.kribo.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One GROUP BY row for the folder picker at scale. */
data class FolderNoteCount(
    @ColumnInfo(name = "folderId") val folderId: Long?,
    @ColumnInfo(name = "count") val count: Int,
)

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY isPinned DESC, updatedAt DESC")
    fun observeByFolder(folderId: Long): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE title LIKE '%' || :query || '%' " +
            "OR plainTextIndex LIKE '%' || :query || '%' " +
            "OR ocrTextIndex LIKE '%' || :query || '%' " +
            "ORDER BY isPinned DESC, updatedAt DESC",
    )
    fun search(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    /** Notes per folder for the 500-folder picker (null folderId = no folder). */
    @Query("SELECT folderId AS folderId, COUNT(*) AS count FROM notes GROUP BY folderId")
    fun observeFolderCounts(): Flow<List<FolderNoteCount>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long?, updatedAt: Long)
}
