package com.blackeye.kribo.ui.home

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackeye.kribo.AppContainer
import com.blackeye.kribo.data.DEFAULT_FOLDER_GENERAL
import com.blackeye.kribo.data.FolderEntity
import com.blackeye.kribo.data.NoteEntity
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.previewText
import com.blackeye.kribo.ocr.OcrQuotaStore
import com.blackeye.kribo.ui.common.SearchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

data class NoteListItem(
    val note: NoteEntity,
    val snippet: String,
    val matchedOcr: Boolean,
    val hasOcr: Boolean,
    val tasksDone: Int,
    val tasksTotal: Int,
    val attachmentPath: String?,
)

enum class ScanPhase { IDLE, SCANNING, COMPLETE, ERROR }

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val page: Int = 0,
    val totalPages: Int = 0,
    val noteId: Long? = null,
)

enum class TransferPhase { IDLE, RUNNING, DONE, ERROR }

data class ImportUiState(
    val phase: TransferPhase = TransferPhase.IDLE,
    val notes: Int = 0,
    val attachments: Int = 0,
)

data class BackupUiState(
    val phase: TransferPhase = TransferPhase.IDLE,
    val done: Int = 0,
    val total: Int = 0,
)

/**
 * Single library state shared by Home, Search and Settings. Holds the selected folder, the
 * instant search query, scan progress and import/export progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.noteRepository

    private val selectedFolderId = MutableStateFlow<Long?>(null)
    val activeFolderId: StateFlow<Long?> = selectedFolderId.asStateFlow()

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<NoteListItem>> = combine(selectedFolderId, query) { folder, text ->
        folder to text
    }.flatMapLatest { (folder, text) ->
        // Accent-insensitive matching cannot be delegated to SQLite LIKE, so the folder flow is
        // filtered in memory with Normalizer.Form.NFD. It keeps `cancion` finding `canción`.
        repository.observeNotes(folder).map { list ->
            list.filter { note -> text.isBlank() || note.matchesQuery(text) }
                .map { note -> note.toListItem(text) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scanState = MutableStateFlow(ScanUiState())
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _paywallVisible = MutableStateFlow(false)
    val paywallVisible: StateFlow<Boolean> = _paywallVisible.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _freeScansLeft = MutableStateFlow(OcrQuotaStore.FREE_OCR_LIMIT)
    val freeScansLeft: StateFlow<Int> = _freeScansLeft.asStateFlow()

    private val attachmentPathCache = mutableMapOf<Long, String?>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            container.billingManager.state.collect {
                _freeScansLeft.value = container.quotaStore.remaining(it.owned)
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun selectFolder(folderId: Long?) {
        selectedFolderId.value = folderId
    }

    fun clearFolderSelection() {
        selectedFolderId.value = null
    }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val folderId = selectedFolderId.value ?: DEFAULT_FOLDER_GENERAL
            val noteId = withContext(Dispatchers.IO) {
                repository.createNote(
                    folderId = folderId,
                    blocks = listOf(
                        com.blackeye.kribo.data.blocks.TitleBlock(),
                        com.blackeye.kribo.data.blocks.ParagraphBlock(),
                    ),
                )
            }
            onCreated(noteId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNote(noteId)
        }
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPinned(note.id, !note.isPinned)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createFolder(name)
        }
    }

    fun renameFolder(folderId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameFolder(folderId, name)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = repository.deleteFolder(folderId)
            if (deleted) {
                selectedFolderId.update { current -> if (current == folderId) null else current }
            }
        }
    }

    fun isPro(): Boolean = container.billingManager.state.value.owned

    fun proPrice(): String? = container.billingManager.state.value.formattedPrice

    /** Quota gate. Returns false and opens the paywall when the free tier is exhausted. */
    fun canStartScan(): Boolean {
        if (isPro() || _freeScansLeft.value > 0) return true
        _paywallVisible.value = true
        return false
    }

    fun dismissPaywall() {
        _paywallVisible.value = false
    }

    fun onScanLaunchFailed() {
        _scanState.value = ScanUiState(phase = ScanPhase.ERROR)
    }

    fun cancelScan() {
        _scanState.value = ScanUiState()
    }

    /**
     * Copies and optimizes every page, runs PP-OCRv5 off the main thread and stores the complete
     * extracted text in the "Recibos" folder. The engine is only instantiated here — never during
     * app start. A free scan is consumed only after at least one page returns real OCR text.
     */
    fun processScan(uris: List<Uri>, contentResolver: ContentResolver) {
        if (uris.isEmpty()) return
        _scanState.value = ScanUiState(
            phase = ScanPhase.SCANNING,
            page = 0,
            totalPages = uris.size,
        )
        viewModelScope.launch {
            var noteId: Long? = null
            var savedPages = 0
            var ocrPages = 0
            try {
                val id = withContext(Dispatchers.IO) {
                    // The starter folder is normally id 2, but it can be deleted by the user:
                    // recreate it by name instead of failing the foreign key.
                    val folderId = repository.findOrCreateFolder(SCAN_FOLDER_NAME)
                    repository.createNote(folderId = folderId)
                }
                noteId = id
                val blocks = mutableListOf<NoteBlock>()

                uris.forEachIndexed { index, uri ->
                    _scanState.update { it.copy(page = index + 1) }
                    val file = container.attachmentStore.saveScanPage(
                        contentResolver = contentResolver,
                        noteId = id,
                        uri = uri,
                        pageIndex = index,
                    ) ?: return@forEachIndexed
                    savedPages++

                    val text = container.ocrSubsystem.recognize(file)
                        .orEmpty()
                        .trim()
                    if (text.isNotBlank()) ocrPages++

                    val attachmentId = withContext(Dispatchers.IO) {
                        repository.addAttachment(
                            noteId = id,
                            filePath = file.absolutePath,
                            mimeType = "image/jpeg",
                            extractedText = text,
                        )
                    }
                    blocks += AttachmentBlock(
                        attachmentId = attachmentId,
                        fileName = file.name,
                        mimeType = "image/jpeg",
                        previewText = text,
                        pageNumber = index + 1,
                    )
                }

                if (savedPages == 0) {
                    withContext(Dispatchers.IO) { repository.deleteNote(id) }
                    _scanState.value = ScanUiState(phase = ScanPhase.ERROR)
                    return@launch
                }

                // saveBlocks rebuilds plainTextIndex + ocrTextIndex with the full OCR text.
                withContext(Dispatchers.IO) { repository.saveBlocks(id, blocks) }

                if (ocrPages > 0) {
                    container.quotaStore.consume()
                    _freeScansLeft.value = container.quotaStore.remaining(isPro())
                }

                // A new scan must not be hidden by an old search filter.
                query.value = ""
                _scanState.value = ScanUiState(phase = ScanPhase.COMPLETE, noteId = id)
            } catch (throwable: Throwable) {
                noteId?.let { id ->
                    withContext(Dispatchers.IO) { repository.deleteNote(id) }
                }
                _scanState.value = ScanUiState(phase = ScanPhase.ERROR)
            }
        }
    }

    fun acknowledgeScan() {
        _scanState.value = ScanUiState()
    }

    fun importFile(displayName: String, input: InputStream) {
        _importState.value = ImportUiState(phase = TransferPhase.RUNNING)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    input.use { stream ->
                        container.importService.import(
                            displayName = displayName,
                            input = stream,
                            defaultFolderId = selectedFolderId.value ?: DEFAULT_FOLDER_GENERAL,
                        )
                    }
                }
            }
            result.onSuccess { summary ->
                _importState.value = ImportUiState(
                    phase = TransferPhase.DONE,
                    notes = summary.notes,
                    attachments = summary.attachments,
                )
            }.onFailure {
                _importState.value = ImportUiState(phase = TransferPhase.ERROR)
            }
        }
    }

    fun acknowledgeImport() {
        _importState.value = ImportUiState()
    }

    fun exportBackup(uri: Uri, contentResolver: ContentResolver) {
        _backupState.value = BackupUiState(phase = TransferPhase.RUNNING)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        container.exportService.writeBackup(output) { done, total ->
                            _backupState.value = BackupUiState(
                                phase = TransferPhase.RUNNING,
                                done = done,
                                total = total,
                            )
                        }
                    } ?: error("Could not open output stream")
                }
            }
            result.onSuccess { total ->
                _backupState.value = BackupUiState(
                    phase = TransferPhase.DONE,
                    done = total,
                    total = total,
                )
            }.onFailure {
                _backupState.value = BackupUiState(phase = TransferPhase.ERROR)
            }
        }
    }

    fun acknowledgeBackup() {
        _backupState.value = BackupUiState()
    }

    /** Creates a note from the bottom capture bar without leaving Home. */
    fun quickCapture(text: String, onSaved: () -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val folderId = selectedFolderId.value ?: DEFAULT_FOLDER_GENERAL
            withContext(Dispatchers.IO) {
                repository.createNote(
                    folderId = folderId,
                    blocks = listOf(ParagraphBlock(text = clean)),
                )
            }
            onSaved()
        }
    }

    private suspend fun NoteEntity.toListItem(activeQuery: String): NoteListItem {
        val blocks = BlockCodec.decode(blocksJson)
        val tasks = blocks.filterIsInstance<TaskBlock>()
        val hasAttachment = blocks.any { it is AttachmentBlock }
        val base = NoteListItem(
            note = this,
            snippet = previewText,
            matchedOcr = false,
            hasOcr = ocrTextIndex.isNotBlank(),
            tasksDone = tasks.count { it.checked },
            tasksTotal = tasks.size,
            attachmentPath = if (hasAttachment) attachmentPathFor(id) else null,
        )
        if (activeQuery.isBlank()) return base

        val inOcr = SearchText.contains(ocrTextIndex, activeQuery)
        val inPlain = SearchText.contains(plainTextIndex, activeQuery)
        val inTitle = SearchText.contains(title, activeQuery)
        val source = when {
            inOcr -> ocrTextIndex
            inPlain -> plainTextIndex
            inTitle -> title
            else -> plainTextIndex.ifBlank { title }
        }
        // The window is centred on the first match, so the highlighted word is never cut off.
        val snippet = SearchText.snippet(source, activeQuery)
        return base.copy(snippet = snippet, matchedOcr = inOcr)
    }

    private suspend fun attachmentPathFor(noteId: Long): String? {
        if (attachmentPathCache.containsKey(noteId)) return attachmentPathCache[noteId]
        val path = repository.getAttachments(noteId).firstOrNull()?.filePath
        attachmentPathCache[noteId] = path
        return path
    }
}

private const val SCAN_FOLDER_NAME = "Recibos"

/** Accent/case-insensitive match across title, body and OCR text. */
private fun NoteEntity.matchesQuery(query: String): Boolean =
    SearchText.contains(title, query) ||
        SearchText.contains(plainTextIndex, query) ||
        SearchText.contains(ocrTextIndex, query)
