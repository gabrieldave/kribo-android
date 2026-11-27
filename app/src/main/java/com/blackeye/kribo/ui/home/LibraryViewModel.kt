package com.blackeye.kribo.ui.home

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackeye.kribo.AppContainer
import com.blackeye.kribo.data.DEFAULT_FOLDER_GENERAL
import com.blackeye.kribo.data.DEFAULT_FOLDER_RECEIPTS
import com.blackeye.kribo.data.FolderEntity
import com.blackeye.kribo.data.NoteEntity
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.newNoteBlocks
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import com.blackeye.kribo.data.previewText
import com.blackeye.kribo.data.titleText
import com.blackeye.kribo.data.export.NoteExportPayload
import com.blackeye.kribo.data.export.buildCopyNotesText
import com.blackeye.kribo.billing.ProLimits
import com.blackeye.kribo.ocr.OcrQuotaStore
import com.blackeye.kribo.ui.billing.PaywallReason
import com.blackeye.kribo.scan.DocumentPageProcessor
import com.blackeye.kribo.ui.common.SearchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

data class NoteListItem(
    val note: NoteEntity,
    val snippet: String,
    val matchedOcr: Boolean,
    val hasOcr: Boolean,
    val tasksDone: Int,
    val tasksTotal: Int,
    val attachmentPath: String?,
)

/** Home ordering (#41): pinned notes always stay on top, then the chosen key. */
enum class NoteSort { RECENT, TITLE_ASC, OLDEST }

/**
 * Resolves which folder to observe on Home: selecting a folder always filters to that folder
 * when the search box is empty; "All notes" scope only overrides it while searching.
 */
fun resolveScopeFolder(
    folderId: Long?,
    query: String,
    searchAllScope: Boolean,
): Long? = if (query.isNotBlank() && searchAllScope) null else folderId

/** Pinned first, then by [sort]; the list in memory stays keyed by id for stable diffs. */
fun sortNoteItems(items: List<NoteListItem>, sort: NoteSort): List<NoteListItem> =
    items.sortedWith { a, b ->
        val pinned = b.note.isPinned.compareTo(a.note.isPinned)
        if (pinned != 0) return@sortedWith pinned
        when (sort) {
            NoteSort.RECENT -> b.note.updatedAt.compareTo(a.note.updatedAt)
            NoteSort.OLDEST -> a.note.updatedAt.compareTo(b.note.updatedAt)
            NoteSort.TITLE_ASC -> SearchText.fold(a.note.titleText)
                .compareTo(SearchText.fold(b.note.titleText))
        }
    }

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

/** Localized copy for the one-time interactive welcome note. */
data class WelcomeNoteText(
    val title: String,
    val subtitle: String,
    val taskOne: String,
    val taskTwo: String,
    val toggleTitle: String,
    val toggleContent: String,
    val quote: String,
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

    /** Search scope: true = every note, false = only inside [activeFolderId]. */
    private val searchAllScope = MutableStateFlow(true)
    val searchAllScopeActive: StateFlow<Boolean> = searchAllScope.asStateFlow()

    private val sortMode = MutableStateFlow(NoteSort.RECENT)
    val sortModeActive: StateFlow<NoteSort> = sortMode.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Notes per folder for the picker at 500+ folder scale (key null = no folder). */
    val folderCounts: StateFlow<Map<Long?, Int>> = repository.observeFolderCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // 150ms debounce keeps the accumulator UI at 60fps with 500+ notes in memory.
    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteListItem>> = combine(
        selectedFolderId,
        query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
        searchAllScope,
        sortMode,
    ) { folder, text, allScope, sort -> QueryInput(folder, text, allScope, sort) }
        .flatMapLatest { input ->
            // Accent-insensitive matching cannot be delegated to SQLite LIKE, so the folder flow
            // is filtered in memory with Normalizer.Form.NFD. It keeps `cancion` finding `canción`.
            val scopeFolder = resolveScopeFolder(input.folder, input.text, input.allScope)
            repository.observeNotes(scopeFolder).map { list ->
                sortNoteItems(
                    list.filter { note -> input.text.isBlank() || note.matchesQuery(input.text) }
                        .map { note -> note.toListItem(input.text) },
                    input.sort,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Folders whose name matches the current query, for one-tap jumping. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val matchingFolders: StateFlow<List<FolderEntity>> = combine(
        folders,
        query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
    ) { all, text -> all.filter { text.isBlank() || SearchText.contains(it.name, text) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class QueryInput(
        val folder: Long?,
        val text: String,
        val allScope: Boolean,
        val sort: NoteSort,
    )

    private val _scanState = MutableStateFlow(ScanUiState())
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _paywallReason = MutableStateFlow<PaywallReason?>(null)
    val paywallReason: StateFlow<PaywallReason?> = _paywallReason.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _freeScansLeft = MutableStateFlow(OcrQuotaStore.FREE_OCR_LIMIT)
    val freeScansLeft: StateFlow<Int> = _freeScansLeft.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val attachmentPathCache = mutableMapOf<Long, String?>()
    private var welcomeNoteChecked = false

    init {
        viewModelScope.launch(Dispatchers.Default) {
            container.entitlementRepository.isPro.collect { pro ->
                _freeScansLeft.value = container.quotaStore.remaining(pro)
            }
        }
    }

    fun onQueryChange(value: String) {
        clearSelection()
        query.value = value
    }

    fun clearQuery() {
        clearSelection()
        query.value = ""
    }

    fun setSearchScopeAll(all: Boolean) {
        searchAllScope.value = all
    }

    fun setSortMode(sort: NoteSort) {
        sortMode.value = sort
    }

    fun selectFolder(folderId: Long?) {
        clearSelection()
        selectedFolderId.value = folderId
    }

    fun clearFolderSelection() {
        clearSelection()
        selectedFolderId.value = null
    }

    fun startSelection(noteId: Long) {
        _selectionMode.value = true
        _selectedIds.update { it + noteId }
    }

    fun toggleSelection(noteId: Long) {
        val next = _selectedIds.value.toMutableSet().apply {
            if (contains(noteId)) remove(noteId) else add(noteId)
        }
        _selectedIds.value = next
        if (next.isEmpty()) _selectionMode.value = false
    }

    fun selectAll(noteIds: Collection<Long>) {
        _selectedIds.value = noteIds.toSet()
        if (noteIds.isNotEmpty()) _selectionMode.value = true
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _selectionMode.value = false
    }

    fun deleteNotes(noteIds: Set<Long>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            noteIds.forEach {
                repository.deleteNote(it)
                attachmentPathCache.remove(it)
            }
        }
        clearSelection()
    }

    fun setPinnedFor(noteIds: Set<Long>, pinned: Boolean) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            noteIds.forEach { repository.setPinned(it, pinned) }
        }
        clearSelection()
    }

    fun moveNotesToFolder(noteIds: Set<Long>, folderId: Long?) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            noteIds.forEach { repository.moveToFolder(it, folderId) }
        }
        clearSelection()
    }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val folderId = selectedFolderId.value ?: DEFAULT_FOLDER_GENERAL
            val noteId = withContext(Dispatchers.IO) {
                repository.createNote(
                    folderId = folderId,
                    blocks = newNoteBlocks(),
                )
            }
            onCreated(noteId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNote(noteId)
            attachmentPathCache.remove(noteId)
        }
    }

    /**
     * Seeds a single interactive welcome note on a truly fresh library, then remembers it.
     * Existing users (DB with notes) are marked as seeded without creating anything.
     */
    fun ensureWelcomeNote(text: WelcomeNoteText) {
        if (welcomeNoteChecked) return
        welcomeNoteChecked = true
        viewModelScope.launch {
            if (container.uiPreferences.isWelcomeNoteSeeded()) return@launch
            val existing = withContext(Dispatchers.IO) { repository.noteCount() }
            if (existing > 0) {
                withContext(Dispatchers.IO) { container.uiPreferences.markWelcomeNoteSeeded() }
                return@launch
            }
            val blocks = listOf(
                TitleBlock(text = text.title),
                TitleBlock(text = text.subtitle, level = 2),
                TaskBlock(text = text.taskOne),
                TaskBlock(text = text.taskTwo),
                ToggleBlock(
                    title = text.toggleTitle,
                    content = text.toggleContent,
                    expanded = false,
                ),
                QuoteBlock(text = text.quote),
            )
            withContext(Dispatchers.IO) {
                repository.createNote(
                    folderId = DEFAULT_FOLDER_GENERAL,
                    blocks = blocks,
                    title = text.title,
                )
                container.uiPreferences.markWelcomeNoteSeeded()
            }
        }
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPinned(note.id, !note.isPinned)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val customCount = withContext(Dispatchers.IO) {
                repository.getAllFolders().count {
                    it.id != DEFAULT_FOLDER_GENERAL && it.id != DEFAULT_FOLDER_RECEIPTS
                }
            }
            if (!ProLimits.canCreateFolder(customCount, isPro())) {
                openPaywall(PaywallReason.FOLDERS_LIMIT)
                return@launch
            }
            withContext(Dispatchers.IO) { repository.createFolder(name) }
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

    fun isPro(): Boolean = container.entitlementRepository.isPro.value

    /** Quota gate. Returns false and opens the paywall when the free tier is exhausted. */
    fun canStartScan(): Boolean {
        if (isPro() || _freeScansLeft.value > 0) return true
        openPaywall(PaywallReason.OCR_LIMIT)
        return false
    }

    fun openPaywall(reason: PaywallReason) {
        _paywallReason.value = reason
    }

    fun dismissPaywall() {
        _paywallReason.value = null
    }

    fun restorePurchases(onResult: (Boolean) -> Unit) {
        container.entitlementRepository.restore(onResult)
    }

    fun onScanLaunchFailed() {
        _scanState.value = ScanUiState(phase = ScanPhase.ERROR)
    }

    fun cancelScan() {
        _scanState.value = ScanUiState()
    }

    /**
     * Full ScanOffline-quality pipeline: raw scanner page -> EXIF/tiled decode ->
     * spine split for double-page books -> UVDoc flatten -> shadow/finger cleanup ->
     * PP-OCRv5 (+ ML Kit fallback). Both the raw and flattened candidates are OCR'd and
     * the richer text wins. Everything runs off the main thread; models stay lazy.
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
            val work = File(container.scanWorkRoot, UUID.randomUUID().toString()).apply { mkdirs() }
            try {
                val id = withContext(Dispatchers.IO) {
                    // The starter folder is normally id 2, but it can be deleted by the user:
                    // recreate it by name instead of failing the foreign key.
                    val folderId = repository.findOrCreateFolder(SCAN_FOLDER_NAME)
                    repository.createNote(folderId = folderId)
                }
                noteId = id
                val candidates = ArrayList<DocumentPageProcessor.PageCandidate>()

                uris.forEachIndexed { index, uri ->
                    val source = container.attachmentStore.copyScanSource(
                        contentResolver = contentResolver,
                        uri = uri,
                        destinationDir = File(work, "source-$index").apply { mkdirs() },
                    ) ?: return@forEachIndexed
                    val processed = container.pageProcessor.process(
                        input = source,
                        outputDir = File(work, "out-$index").apply { mkdirs() },
                    )
                    candidates += processed
                    _scanState.update {
                        it.copy(
                            page = candidates.size,
                            totalPages = candidates.size + (uris.size - index - 1),
                        )
                    }
                }

                if (candidates.isEmpty()) {
                    withContext(Dispatchers.IO) { repository.deleteNote(id) }
                    _scanState.value = ScanUiState(phase = ScanPhase.ERROR)
                    return@launch
                }

                _scanState.update { it.copy(page = 0, totalPages = candidates.size) }
                val blocks = mutableListOf<NoteBlock>()
                val ocrTexts = mutableListOf<String>()
                var savedPages = 0
                var ocrPages = 0

                candidates.forEachIndexed { index, candidate ->
                    _scanState.update { it.copy(page = index + 1) }
                    val (text, chosen) = recognizeBestCandidate(candidate)
                    val stored = container.attachmentStore.saveProcessedPage(
                        noteId = id,
                        source = chosen,
                        pageNumber = index + 1,
                    ) ?: return@forEachIndexed
                    savedPages++
                    if (text.isNotBlank()) {
                        ocrPages++
                        ocrTexts += text
                    }

                    val attachmentId = withContext(Dispatchers.IO) {
                        repository.addAttachment(
                            noteId = id,
                            filePath = stored.absolutePath,
                            mimeType = "image/jpeg",
                            extractedText = text,
                        )
                    }
                    blocks += AttachmentBlock(
                        attachmentId = attachmentId,
                        fileName = stored.name,
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

                // Automatic, editable headline derived from the recognised text. It is a real
                // TitleBlock, so the user can rename the scan directly in the editor.
                val autoTitle = autoTitleFromOcr(ocrTexts)
                if (autoTitle.isNotBlank()) {
                    blocks.add(0, TitleBlock(text = autoTitle))
                }

                // Persist the blocks (and therefore the title + search index) before consuming
                // the free quota or navigating, so a failed scan never costs a scan.
                withContext(Dispatchers.IO) { repository.saveBlocks(id, blocks) }

                if (ocrPages > 0) {
                    container.quotaStore.consume()
                    _freeScansLeft.value = container.quotaStore.remaining(isPro())
                }

                // A new scan must not stay hidden behind an old folder/search filter: the note
                // is already committed to Room, so both "Recibos" and "Todas" refresh at once.
                query.value = ""
                selectedFolderId.value = null
                attachmentPathCache.clear()
                _scanState.value = ScanUiState(phase = ScanPhase.COMPLETE, noteId = id)
            } catch (throwable: Throwable) {
                noteId?.let { id ->
                    withContext(Dispatchers.IO) { repository.deleteNote(id) }
                }
                _scanState.value = ScanUiState(phase = ScanPhase.ERROR)
            } finally {
                withContext(Dispatchers.IO) { work.deleteRecursively() }
            }
        }
    }

    /**
     * OCRs both candidates and keeps the one with more prose. The raw image protects against
     * UVDoc over-warping a flat receipt; the flattened one recovers curved book pages.
     */
    private suspend fun recognizeBestCandidate(
        candidate: DocumentPageProcessor.PageCandidate,
    ): Pair<String, File> {
        val rawText = container.ocrSubsystem.recognize(candidate.raw).orEmpty().trim()
        val flat = candidate.flat
        if (flat == null) return rawText to candidate.raw
        val flatText = container.ocrSubsystem.recognize(flat).orEmpty().trim()
        return if (textScore(flatText) >= textScore(rawText)) {
            flatText to flat
        } else {
            rawText to candidate.raw
        }
    }

    private fun textScore(text: String): Int {
        if (text.isBlank()) return 0
        val letters = text.count { it.isLetter() }
        val words = text.split(Regex("\\s+")).count { it.length >= 2 }
        return letters * 2 + words * 3
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

    /**
     * #43: builds the clean payloads (blocks + folder + OCR) for one or several selected
     * notes so Home can combine them into a single `.txt` for the AI share sheet.
     */
    suspend fun buildAiPayloads(ids: Set<Long>): List<NoteExportPayload> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val folderNames = repository.getAllFolders().associate { it.id to it.name }
        ids.mapNotNull { id ->
            val note = repository.getNote(id) ?: return@mapNotNull null
            val ocr = repository.getAttachments(id)
                .mapNotNull { attachment ->
                    attachment.extractedText
                        .takeIf { it.isNotBlank() }
                        ?.let { attachment.id to it }
                }
                .toMap()
            NoteExportPayload(
                title = note.titleText,
                folder = note.folderId?.let(folderNames::get).orEmpty(),
                blocks = repository.getBlocks(id),
                ocrTexts = ocr,
            )
        }
    }

    /** Builds clean plain text for one or more selected notes (including OCR) for clipboard copy. */
    suspend fun buildCopyText(ids: List<Long>): String = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext ""
        val payloads = ids.mapNotNull { id ->
            val note = repository.getNote(id) ?: return@mapNotNull null
            val ocr = repository.getAttachments(id)
                .mapNotNull { attachment ->
                    attachment.extractedText
                        .takeIf { it.isNotBlank() }
                        ?.let { attachment.id to it }
                }
                .toMap()
            NoteExportPayload(
                title = note.title,
                folder = "",
                blocks = repository.getBlocks(id),
                ocrTexts = ocr,
            )
        }
        buildCopyNotesText(payloads)
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
private const val SEARCH_DEBOUNCE_MS = 150L

/**
 * Turns the first meaningful OCR line into a short, editable note title. Control characters,
 * repeated spaces and edge punctuation are removed; accents and letters stay untouched.
 */
private fun autoTitleFromOcr(ocrTexts: List<String>): String {
    val candidate = ocrTexts.asSequence()
        .flatMap { it.lineSequence() }
        .map { line ->
            line.replace(Regex("\\p{Cntrl}+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        .firstOrNull { line -> line.count(Char::isLetterOrDigit) >= 3 }
        ?: return ""
    return candidate
        .trim(' ', '-', '–', '—', '·', '|', '.', ',', ';', ':', '/', '\\')
        .take(60)
        .trim()
}

/** Accent/case-insensitive match across title, body and OCR text. */
private fun NoteEntity.matchesQuery(query: String): Boolean =
    SearchText.contains(title, query) ||
        SearchText.contains(plainTextIndex, query) ||
        SearchText.contains(ocrTextIndex, query)
