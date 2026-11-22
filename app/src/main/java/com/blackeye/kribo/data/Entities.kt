package com.blackeye.kribo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index(value = ["name"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val iconKey: String = "folder",
    val colorHex: String = "#00D8FF",
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val folderId: Long? = null,
    val title: String,
    val blocksJson: String,
    val plainTextIndex: String,
    val ocrTextIndex: String,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["noteId"])],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val noteId: Long,
    val filePath: String,
    val mimeType: String,
    val extractedText: String = "",
    val createdAt: Long,
)
