package com.blackeye.kribo.ui.note

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackeye.kribo.data.AttachmentEntity
import com.blackeye.kribo.data.FolderEntity
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.ocr.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteEditorViewModel(
    private val repository: NoteRepository,
    private val attachmentStore: AttachmentStore,
    private val noteId: Long,
    initialSearchQuery: String = "",
) : ViewModel() {

    var blocks by mutableStateOf<List<NoteBlock>>(emptyList())
        private set

    var attachments by mutableStateOf<List<AttachmentEntity>>(emptyList())
        private set

    var isPinned by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var focusedBlockId by mutableStateOf<String?>(null)

    /** Search term inherited from Home; drives auto-scroll and in-editor highlighting. */
    var highlightQuery by mutableStateOf(initialSearchQuery.trim())
        private set

    var isAddingImage by mutableStateOf(false)
        private set

    var imageError by mutableStateOf(false)
        private set

    var folderId by mutableStateOf<Long?>(null)
        private set

    var folders by mutableStateOf<List<FolderEntity>>(emptyList())
        private set

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Loaded(
                    note = repository.getNote(noteId),
                    blocks = repository.getBlocks(noteId),
                    attachments = repository.getAttachments(noteId),
                    folders = repository.getAllFolders(),
                )
            }
            blocks = loaded.blocks
            attachments = loaded.attachments
            isPinned = loaded.note?.isPinned ?: false
            folderId = loaded.note?.folderId
            folders = loaded.folders
            isLoading = false
        }
    }

    fun onFocus(blockId: String) {
        focusedBlockId = blockId
    }

    fun clearHighlight() {
        highlightQuery = ""
    }

    fun clearImageError() {
        imageError = false
    }

    fun updateBlock(blockId: String, transform: (NoteBlock) -> NoteBlock) {
        blocks = blocks.map { block ->
            if (block.id == blockId) transform(block) else block
        }
        scheduleSave()
    }

    fun addBlock(block: NoteBlock) {
        val index = blocks.indexOfFirst { it.id == focusedBlockId }
        val insertAt = if (index >= 0) index + 1 else blocks.size
        blocks = blocks.toMutableList().apply { add(insertAt, block) }
        focusedBlockId = block.id
        scheduleSave()
    }

    fun removeBlock(blockId: String) {
        val target = blocks.firstOrNull { it.id == blockId }
        blocks = blocks.filterNot { it.id == blockId }
        if (focusedBlockId == blockId) focusedBlockId = null
        scheduleSave(immediate = true)

        if (target is AttachmentBlock && target.attachmentId != null) {
            val attachmentId = target.attachmentId
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteAttachment(noteId, attachmentId)
            }
        }
    }

    /** Gallery/camera picture → private compressed JPEG + visible block at the end of the note. */
    fun addImage(uri: Uri, contentResolver: ContentResolver) {
        if (isAddingImage) return
        isAddingImage = true
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    attachmentStore.saveImage(contentResolver, noteId, uri)
                }
                if (file == null) {
                    imageError = true
                    return@launch
                }
                val attachmentId = withContext(Dispatchers.IO) {
                    repository.addAttachment(
                        noteId = noteId,
                        filePath = file.absolutePath,
                        mimeType = "image/jpeg",
                        extractedText = "",
                    )
                }
                val block = AttachmentBlock(
                    attachmentId = attachmentId,
                    fileName = file.name,
                    mimeType = "image/jpeg",
                    previewText = "",
                )
                val index = blocks.indexOfFirst { it.id == focusedBlockId }
                val insertAt = if (index >= 0) index + 1 else blocks.size
                val updated = blocks.toMutableList().apply { add(insertAt, block) }
                blocks = updated
                focusedBlockId = block.id
                val loaded = withContext(Dispatchers.IO) {
                    repository.saveBlocks(noteId, updated)
                    repository.getAttachments(noteId)
                }
                attachments = loaded
            } catch (throwable: Throwable) {
                imageError = true
            } finally {
                isAddingImage = false
            }
        }
    }

    /** Keeps the editable block text and the searchable attachment row in lockstep. */
    fun updateAttachmentText(blockId: String, attachmentId: Long, text: String) {
        val edited = blocks.map { block ->
            if (block.id == blockId && block is AttachmentBlock) {
                block.copy(previewText = text)
            } else {
                block
            }
        }
        blocks = edited
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAttachmentText(noteId, attachmentId, text)
            repository.saveBlocks(noteId, edited)
        }
    }

    fun moveToFolder(targetFolderId: Long?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.moveToFolder(noteId, targetFolderId) }
            folderId = targetFolderId
        }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteNote(noteId) }
            onDeleted()
        }
    }

    fun togglePinned() {
        isPinned = !isPinned
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPinned(noteId, isPinned)
        }
    }

    /** Flushes the debounce and only then leaves the screen. No writes on the main thread. */
    fun flushAndExit(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persist()
            onDone()
        }
    }

    fun refreshAttachments() {
        viewModelScope.launch {
            attachments = withContext(Dispatchers.IO) { repository.getAttachments(noteId) }
        }
    }

    private fun scheduleSave(immediate: Boolean = false) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (!immediate) delay(SAVE_DEBOUNCE_MS)
            persist()
        }
    }

    private suspend fun persist() {
        val snapshot = blocks
        withContext(Dispatchers.IO) {
            repository.saveBlocks(noteId, snapshot)
        }
    }

    private data class Loaded(
        val note: com.blackeye.kribo.data.NoteEntity?,
        val blocks: List<NoteBlock>,
        val attachments: List<AttachmentEntity>,
        val folders: List<FolderEntity>,
    )

    companion object {
        const val SAVE_DEBOUNCE_MS = 300L
    }
}
