package com.blackeye.kribo.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackeye.kribo.data.AttachmentEntity
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteEditorViewModel(
    private val repository: NoteRepository,
    private val noteId: Long,
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

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val (note, loadedBlocks, loadedAttachments) = withContext(Dispatchers.IO) {
                Triple(
                    repository.getNote(noteId),
                    repository.getBlocks(noteId),
                    repository.getAttachments(noteId),
                )
            }
            blocks = loadedBlocks
            attachments = loadedAttachments
            isPinned = note?.isPinned ?: false
            isLoading = false
        }
    }

    fun onFocus(blockId: String) {
        focusedBlockId = blockId
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
        blocks = blocks.filterNot { it.id == blockId }
        if (focusedBlockId == blockId) focusedBlockId = null
        scheduleSave(immediate = true)
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

    companion object {
        const val SAVE_DEBOUNCE_MS = 300L
    }
}
