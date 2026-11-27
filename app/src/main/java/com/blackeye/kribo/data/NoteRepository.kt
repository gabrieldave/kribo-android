package com.blackeye.kribo.data

import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(
    private val database: KriboDatabase,
    private val attachmentsRoot: File,
    private val onNoteDeleted: ((noteId: Long, deletedAt: Long) -> Unit)? = null,
) {
    private val noteDao = database.noteDao()
    private val folderDao = database.folderDao()
    private val attachmentDao = database.attachmentDao()

    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeAll()

    fun observeNotes(folderId: Long?): Flow<List<NoteEntity>> =
        if (folderId == null) noteDao.observeAll() else noteDao.observeByFolder(folderId)

    /** Key per folder (null = no folder); only folders with notes appear. */
    fun observeFolderCounts(): Flow<Map<Long?, Int>> =
        noteDao.observeFolderCounts().map { rows -> rows.associate { it.folderId to it.count } }

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.search(query.trim())

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    suspend fun getNote(id: Long): NoteEntity? = noteDao.getById(id)

    suspend fun getBlocks(noteId: Long): List<NoteBlock> =
        BlockCodec.decode(noteDao.getById(noteId)?.blocksJson)

    suspend fun getAttachments(noteId: Long): List<AttachmentEntity> =
        attachmentDao.getForNote(noteId)

    suspend fun getAttachment(id: Long): AttachmentEntity? = attachmentDao.getById(id)

    suspend fun createNote(
        folderId: Long?,
        blocks: List<NoteBlock> = emptyList(),
        title: String = BlockCodec.derivedTitle(blocks),
    ): Long {
        val now = System.currentTimeMillis()
        return noteDao.insert(
            NoteEntity(
                folderId = folderId,
                title = title.take(120),
                blocksJson = BlockCodec.encode(blocks),
                plainTextIndex = BlockCodec.plainText(blocks),
                ocrTextIndex = "",
                isPinned = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun saveBlocks(noteId: Long, blocks: List<NoteBlock>) {
        val existing = noteDao.getById(noteId) ?: return
        val plainText = BlockCodec.plainText(blocks)
        val derivedTitle = BlockCodec.derivedTitle(blocks)
        val ocrText = attachmentDao.getForNote(noteId)
            .mapNotNull { it.extractedText.takeIf(String::isNotBlank) }
            .joinToString(separator = "\n")
        noteDao.update(
            existing.copy(
                // The title field (#38) is the source of truth; derived title is only a fallback
                // so Home/export keep working when the field is left blank.
                title = existing.title.ifBlank { derivedTitle },
                blocksJson = BlockCodec.encode(blocks),
                plainTextIndex = plainText,
                ocrTextIndex = ocrText,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Persists the editable note title shown by the fixed title field in the editor. */
    suspend fun setNoteTitle(noteId: Long, title: String) {
        val existing = noteDao.getById(noteId) ?: return
        noteDao.update(
            existing.copy(
                title = title.take(120),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Changes the per-note editorial font (#42); older notes keep SANS through the default. */
    suspend fun setNoteFont(noteId: Long, fontStyle: String) {
        val existing = noteDao.getById(noteId) ?: return
        noteDao.update(
            existing.copy(
                fontStyle = fontStyle,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun addAttachment(
        noteId: Long,
        filePath: String,
        mimeType: String,
        extractedText: String,
    ): Long {
        val id = attachmentDao.insert(
            AttachmentEntity(
                noteId = noteId,
                filePath = filePath,
                mimeType = mimeType,
                extractedText = extractedText,
                createdAt = System.currentTimeMillis(),
            ),
        )
        refreshOcrIndex(noteId)
        return id
    }

    /** Used by Drive sync to restore a remote note with its original id and timestamp. */
    suspend fun upsertSyncedNote(
        noteId: Long,
        folderId: Long?,
        title: String,
        blocks: List<NoteBlock>,
        updatedAt: Long,
        remoteOcrText: String = "",
        fontStyle: String = "SANS",
        isPinned: Boolean? = null,
    ) {
        val existing = noteDao.getById(noteId)
        val attachmentText = attachmentDao.getForNote(noteId)
            .mapNotNull { it.extractedText.takeIf(String::isNotBlank) }
            .joinToString(separator = "\n")
        val cleanTitle = title.ifBlank { BlockCodec.derivedTitle(blocks) }.take(120)
        val entity = NoteEntity(
            id = noteId,
            folderId = folderId,
            title = cleanTitle,
            blocksJson = BlockCodec.encode(blocks),
            plainTextIndex = BlockCodec.plainText(blocks),
            ocrTextIndex = attachmentText.ifBlank { remoteOcrText },
            isPinned = isPinned ?: existing?.isPinned ?: false,
            createdAt = existing?.createdAt ?: updatedAt,
            updatedAt = updatedAt,
            fontStyle = fontStyle.takeIf { it.isNotBlank() } ?: "SANS",
        )
        if (existing != null) {
            noteDao.update(entity)
        } else {
            noteDao.upsert(entity)
        }
    }

    suspend fun refreshOcrIndex(noteId: Long) {
        val existing = noteDao.getById(noteId) ?: return
        val ocrText = attachmentDao.getForNote(noteId)
            .mapNotNull { it.extractedText.takeIf(String::isNotBlank) }
            .joinToString(separator = "\n")
        noteDao.update(
            existing.copy(
                ocrTextIndex = ocrText,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Persists user edits to the extracted OCR text and keeps the search index in sync. */
    suspend fun updateAttachmentText(noteId: Long, attachmentId: Long, text: String) {
        attachmentDao.updateExtractedText(attachmentId, text)
        refreshOcrIndex(noteId)
    }

    /** Removes the row and its private file when the user deletes an image block. */
    suspend fun deleteAttachment(noteId: Long, attachmentId: Long) {
        val attachment = attachmentDao.getById(attachmentId) ?: return
        attachmentDao.deleteById(attachmentId)
        runCatching { File(attachment.filePath).delete() }
        refreshOcrIndex(noteId)
    }

    suspend fun deleteNote(noteId: Long) {
        onNoteDeleted?.invoke(noteId, System.currentTimeMillis())
        attachmentDao.deleteForNote(noteId)
        noteDao.deleteById(noteId)
        File(attachmentsRoot, noteId.toString()).deleteRecursively()
    }

    suspend fun setPinned(noteId: Long, pinned: Boolean) {
        noteDao.setPinned(noteId, pinned, System.currentTimeMillis())
    }

    suspend fun moveToFolder(noteId: Long, folderId: Long?) {
        noteDao.moveToFolder(noteId, folderId, System.currentTimeMillis())
    }

    suspend fun noteCount(): Int = noteDao.count()

    suspend fun getAllNotes(): List<NoteEntity> = noteDao.getAllNow()

    suspend fun getAllFolders(): List<FolderEntity> = folderDao.getAll()

    suspend fun createFolder(
        name: String,
        iconKey: String = "folder",
        colorHex: String = "#00D8FF",
    ): Long? {
        val clean = name.trim().take(48)
        if (clean.isEmpty()) return null
        if (folderDao.findByName(clean) != null) return null
        return runCatching {
            folderDao.insert(
                FolderEntity(
                    name = clean,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    sortOrder = folderDao.nextSortOrder(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }.getOrNull()
    }

    suspend fun renameFolder(folderId: Long, newName: String): Boolean {
        val clean = newName.trim().take(48)
        if (clean.isEmpty()) return false
        val folder = folderDao.getById(folderId) ?: return false
        val duplicate = folderDao.findByName(clean)
        if (duplicate != null && duplicate.id != folderId) return false
        return runCatching {
            folderDao.update(folder.copy(name = clean))
            true
        }.getOrDefault(false)
    }

    /** General (id 1) is protected. Notes of a deleted folder move back to General. */
    suspend fun deleteFolder(folderId: Long): Boolean {
        if (folderId == DEFAULT_FOLDER_GENERAL) return false
        folderDao.getById(folderId) ?: return false
        folderDao.reassignNotes(folderId, DEFAULT_FOLDER_GENERAL)
        folderDao.deleteById(folderId)
        return true
    }

    suspend fun findOrCreateFolder(
        name: String,
        colorHex: String? = null,
        iconKey: String? = null,
    ): Long {
        val clean = name.trim().take(48).ifBlank { "Importadas" }
        val safeColor = colorHex?.trim()?.takeIf { HEX_COLOR_PATTERN.matches(it) }
        val safeIcon = iconKey?.trim()?.take(32)?.takeIf { it.isNotEmpty() }
        val existing = folderDao.findByName(clean)
        if (existing != null) {
            val updatedColor = safeColor ?: existing.colorHex
            val updatedIcon = safeIcon ?: existing.iconKey
            if (updatedColor != existing.colorHex || updatedIcon != existing.iconKey) {
                folderDao.update(existing.copy(colorHex = updatedColor, iconKey = updatedIcon))
            }
            return existing.id
        }
        createFolder(
            name = clean,
            iconKey = safeIcon ?: "folder",
            colorHex = safeColor ?: "#00D8FF",
        )?.let { return it }
        return folderDao.findByName(clean)?.id ?: DEFAULT_FOLDER_GENERAL
    }

    private companion object {
        val HEX_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    }
}
