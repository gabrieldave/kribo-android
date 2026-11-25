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
import com.blackeye.kribo.data.blocks.BlockInsertion
import com.blackeye.kribo.data.blocks.AccentTone
import com.blackeye.kribo.data.blocks.ActiveStyles
import com.blackeye.kribo.data.blocks.BlockKind
import com.blackeye.kribo.data.blocks.InlineSpan
import com.blackeye.kribo.data.blocks.SpanEditor
import com.blackeye.kribo.data.blocks.SpanType
import com.blackeye.kribo.data.NoteFont
import com.blackeye.kribo.data.blocks.Caret
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.domain.editor.BlockReorder
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

    /** One-shot signal so a freshly inserted block grabs the keyboard and gets a soft pulse. */
    var focusRequestId by mutableStateOf<String?>(null)
        private set

    /** Block created by the user; drives the soft confirmation pulse independently of focus. */
    var createdBlockId by mutableStateOf<String?>(null)
        private set

    /** Current note title, used to name exported files without hardcoded names. */
    var noteTitle by mutableStateOf("")
        private set

    /** Editorial font of the note (#42), persisted per note with a SANS default. */
    var noteFont by mutableStateOf(NoteFont.SANS)
        private set

    /** Styles typed at the caret or applied to the current selection (#42). */
    var activeTypingStyles by mutableStateOf(ActiveStyles())
        private set

    /** Last known caret/selection inside [focusedBlockId]; survives toolbar taps. */
    var activeCaret by mutableStateOf<Caret?>(null)
        private set

    /** Anchor captured when the photo chooser opens; the image lands at the caret after picking. */
    private var pendingImageAnchor: PendingImageAnchor? = null

    private data class PendingImageAnchor(val blockId: String?, val caret: Caret?)

    private data class Snapshot(val blocks: List<NoteBlock>, val title: String)

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

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
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var coalesceKey: String? = null
    private var coalesceAt = 0L

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
            noteTitle = loaded.note?.title.orEmpty()
            noteFont = NoteFont.fromName(loaded.note?.fontStyle)
            isPinned = loaded.note?.isPinned ?: false
            folderId = loaded.note?.folderId
            folders = loaded.folders
            isLoading = false

            // New notes may start typing immediately; existing notes stay in reading mode
            // until the user deliberately taps a block.
            if (loaded.blocks.isNotEmpty() && loaded.blocks.all { it.plainText().isBlank() }) {
                focusRequestId = loaded.blocks.first().id
                createdBlockId = loaded.blocks.first().id
            }
        }
    }

    /** Clears the one-shot keyboard request once the field actually owns focus. */
    fun consumeFocusRequest(blockId: String) {
        if (focusRequestId == blockId) focusRequestId = null
    }

    fun onFocus(blockId: String) {
        if (focusedBlockId != blockId) {
            focusedBlockId = blockId
            activeCaret = null
        }
    }

    /** Remembers where the caret/selection is inside the active block (toolbar never clears it). */
    fun onSelection(blockId: String, start: Int, end: Int, field: BlockField = BlockField.MAIN) {
        if (focusedBlockId != blockId) focusedBlockId = blockId
        activeCaret = Caret(start, end)
        activeField = field
        val block = blocks.firstOrNull { it.id == blockId }
        activeTypingStyles = SpanEditor.activeStylesAt(spansOfBlock(block, field), start, end)
    }

    /** Which sub-field of the active block holds the caret (toggle title vs content). */
    var activeField by mutableStateOf(BlockField.MAIN)
        private set

    private fun spansOfBlock(block: NoteBlock?, field: BlockField = activeField): List<InlineSpan> = when (block) {
        is com.blackeye.kribo.data.blocks.TitleBlock -> block.spans
        is ParagraphBlock -> block.spans
        is com.blackeye.kribo.data.blocks.TaskBlock -> block.spans
        is com.blackeye.kribo.data.blocks.QuoteBlock -> block.spans
        is com.blackeye.kribo.data.blocks.ToggleBlock ->
            if (field == BlockField.TOGGLE_CONTENT) block.contentSpans else block.titleSpans
        else -> emptyList()
    }

    /** Note font change (#42): one undo step since the font changes the whole look. */
    fun updateNoteFont(font: NoteFont) {
        if (font == noteFont) return
        recordHistory()
        noteFont = font
        scheduleSave()
    }

    /**
     * Toolbar formatting (#42): applies or removes a style on the caret selection. With no
     * selection, B/I/U/S and colors arm the typing styles instead.
     */
    fun toggleFormatting(blockId: String, type: SpanType, tone: AccentTone? = null) {
        val caret = activeCaret
        val block = blocks.firstOrNull { it.id == blockId } ?: return
        val spans = spansOfBlock(block)
        val textLength = textLengthOf(block)
        if (caret == null || caret.start >= caret.end) {
            activeTypingStyles = when (type) {
                SpanType.BOLD -> activeTypingStyles.copy(bold = !activeTypingStyles.bold)
                SpanType.ITALIC -> activeTypingStyles.copy(italic = !activeTypingStyles.italic)
                SpanType.UNDERLINE -> activeTypingStyles.copy(underline = !activeTypingStyles.underline)
                SpanType.STRIKETHROUGH -> activeTypingStyles.copy(
                    strikethrough = !activeTypingStyles.strikethrough,
                )
                SpanType.TEXT_COLOR -> activeTypingStyles.copy(
                    textTone = if (activeTypingStyles.textTone == tone) null else tone,
                )
                SpanType.HIGHLIGHT -> activeTypingStyles.copy(
                    highlightTone = if (activeTypingStyles.highlightTone == tone) null else tone,
                )
            }
            return
        }
        recordHistory(coalesce = blockId)
        val updated = SpanEditor.toggleStyle(spans, caret.start, caret.end, type, tone, textLength)
        blocks = blocks.map { current ->
            if (current.id != blockId) current else replaceSpans(current, updated)
        }
        activeTypingStyles = SpanEditor.activeStylesAt(updated, caret.start, caret.end)
        scheduleSave()
    }

    /** Clears all formatting in the selection (or the whole block with no selection). */
    fun clearFormatting(blockId: String) {
        val caret = activeCaret
        val block = blocks.firstOrNull { it.id == blockId } ?: return
        recordHistory()
        val cleared = SpanEditor.clearFormatting(
            spansOfBlock(block),
            caret?.start ?: 0,
            caret?.end ?: textLengthOf(block),
        )
        blocks = blocks.map { current ->
            if (current.id != blockId) current else replaceSpans(current, cleared)
        }
        activeTypingStyles = ActiveStyles()
        scheduleSave()
    }

    /** Which editable part of a block reports the caret (toggle title vs content). */
    enum class BlockField { MAIN, TOGGLE_TITLE, TOGGLE_CONTENT }

    /** Text input that also shifts/applies spans for the typing styles (#42). */
    fun updateBlockText(blockId: String, newText: String, field: BlockField = activeField) {
        val block = blocks.firstOrNull { it.id == blockId } ?: return
        val oldText = textOfBlock(block, field)
        if (oldText == newText) return
        recordHistory(coalesce = blockId)
        val adjusted = SpanEditor.adjustSpansOnEdit(oldText, newText, spansOfBlock(block, field), activeTypingStyles)
        blocks = blocks.map { current ->
            if (current.id != blockId) current else replaceTextSpans(current, newText, adjusted, field)
        }
        scheduleSave()
    }

    private fun textOfBlock(block: NoteBlock, field: BlockField = activeField): String = when (block) {
        is com.blackeye.kribo.data.blocks.TitleBlock -> block.text
        is ParagraphBlock -> block.text
        is com.blackeye.kribo.data.blocks.TaskBlock -> block.text
        is com.blackeye.kribo.data.blocks.QuoteBlock -> block.text
        is com.blackeye.kribo.data.blocks.ToggleBlock ->
            if (field == BlockField.TOGGLE_CONTENT) block.content else block.title
        else -> ""
    }

    private fun textLengthOf(block: NoteBlock?): Int = textOfBlock(block ?: return 0).length

    private fun replaceSpans(block: NoteBlock, spans: List<InlineSpan>): NoteBlock = when (block) {
        is com.blackeye.kribo.data.blocks.TitleBlock -> block.copy(spans = spans)
        is ParagraphBlock -> block.copy(spans = spans)
        is com.blackeye.kribo.data.blocks.TaskBlock -> block.copy(spans = spans)
        is com.blackeye.kribo.data.blocks.QuoteBlock -> block.copy(spans = spans)
        is com.blackeye.kribo.data.blocks.ToggleBlock ->
            if (activeField == BlockField.TOGGLE_CONTENT) {
                block.copy(contentSpans = spans)
            } else {
                block.copy(titleSpans = spans)
            }
        else -> block
    }

    private fun replaceTextSpans(
        block: NoteBlock,
        text: String,
        spans: List<InlineSpan>,
        field: BlockField = activeField,
    ): NoteBlock =
        when (block) {
            is com.blackeye.kribo.data.blocks.TitleBlock -> block.copy(text = text, spans = spans)
            is ParagraphBlock -> block.copy(text = text, spans = spans)
            is com.blackeye.kribo.data.blocks.TaskBlock -> block.copy(text = text, spans = spans)
            is com.blackeye.kribo.data.blocks.QuoteBlock -> block.copy(text = text, spans = spans)
            is com.blackeye.kribo.data.blocks.ToggleBlock ->
                if (field == BlockField.TOGGLE_CONTENT) {
                    block.copy(content = text, contentSpans = spans)
                } else {
                    block.copy(title = text, titleSpans = spans)
                }
            else -> block
        }

    /** Toolbar action (#37): convert, split or insert at the caret. One undo step. */
    fun applyToolbarAction(newBlock: NoteBlock, kind: BlockKind) {
        val outcome = BlockInsertion.apply(
            blocks = blocks,
            activeId = focusedBlockId,
            caret = activeCaret,
            newBlock = newBlock,
            kind = kind,
        )
        recordHistory()
        blocks = outcome.blocks
        outcome.focusBlockId?.let { adoptFocus(it) }
        scheduleSave()
    }

    /** Captures the caret so the chosen picture lands exactly there after the picker closes. */
    fun beginAttachImage() {
        pendingImageAnchor = PendingImageAnchor(focusedBlockId, activeCaret)
    }

    /** Toggle title typing, with its own spans kept in step (#42). */
    fun updateToggleTitle(blockId: String, newTitle: String) {
        val block = blocks.firstOrNull { it.id == blockId } as? com.blackeye.kribo.data.blocks.ToggleBlock ?: return
        if (block.title == newTitle) return
        recordHistory(coalesce = blockId)
        val adjusted = SpanEditor.adjustSpansOnEdit(block.title, newTitle, block.titleSpans, activeTypingStyles)
        blocks = blocks.map { current ->
            if (current.id != blockId) current else block.copy(title = newTitle, titleSpans = adjusted)
        }
        scheduleSave()
    }

    /** The title field is the rename mechanism (#38); typing coalesces into one undo step. */
    fun updateNoteTitle(raw: String) {
        if (raw == noteTitle) return
        recordHistory(coalesce = TITLE_COALESCE_KEY)
        val previous = noteTitle
        val split = BlockInsertion.splitTitlePaste(raw)
        val head = split.head.take(TITLE_MAX_LENGTH)
        noteTitle = head
        syncDuplicateTitleBlock(previous, head)
        if (split.rest.isNotBlank()) {
            val rest = ParagraphBlock(text = split.rest)
            blocks = listOf(rest) + blocks
            adoptFocus(rest.id)
        }
        scheduleSave()
    }

    /** Pasting into H1/H2 keeps the first line in place and drops the rest into a paragraph. */
    fun applyTitlePaste(blockId: String, pasted: String) {
        val current = blocks.firstOrNull { it.id == blockId } ?: return
        val split = BlockInsertion.splitTitlePaste(pasted)
        if (split.rest.isBlank()) {
            updateCurrentText(blockId, split.head)
            scheduleSave()
            return
        }
        recordHistory()
        val index = blocks.indexOfFirst { it.id == blockId }
        updateCurrentText(blockId, split.head)
        val rest = ParagraphBlock(text = split.rest)
        blocks = blocks.toMutableList().apply { add((index + 1).coerceIn(0, size), rest) }
        adoptFocus(rest.id)
        scheduleSave()
    }

    /** Enter on H1/H2 descends into a fresh paragraph below, never a giant heading. */
    fun insertParagraphBelow(blockId: String) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        recordHistory()
        val paragraph = ParagraphBlock()
        blocks = blocks.toMutableList().apply { add(index + 1, paragraph) }
        adoptFocus(paragraph.id)
        scheduleSave()
    }

    /** Enter on the title field goes to the first paragraph block, creating it when needed. */
    fun focusFirstParagraph() {
        val first = blocks.firstOrNull { it is ParagraphBlock }
        if (first != null) {
            adoptFocus(first.id)
            return
        }
        recordHistory()
        val paragraph = ParagraphBlock()
        blocks = listOf(paragraph) + blocks
        adoptFocus(paragraph.id)
        scheduleSave()
    }

    /** Enter on an empty task converts it into a paragraph; otherwise a new task below. */
    fun exitTaskBelow(blockId: String) {
        val outcome = BlockInsertion.exitBelowTask(blocks, blockId)
        if (outcome.focusBlockId == null) return
        recordHistory()
        blocks = outcome.blocks
        adoptFocus(outcome.focusBlockId)
        scheduleSave()
    }

    /**
     * Double Enter inside a quote or a collapsible section: the break(s) are trimmed and an
     * empty paragraph takes focus below it. An empty quote becomes that paragraph in place.
     */
    fun handleDoubleEnter(blockId: String) {
        val outcome = BlockInsertion.doubleEnterExit(blocks, blockId)
        val focusId = outcome.focusBlockId ?: return
        recordHistory()
        blocks = outcome.blocks
        adoptFocus(focusId)
        scheduleSave()
    }

    /** Bottom "New paragraph" row: reuse a trailing empty paragraph instead of stacking. */
    fun appendParagraph() {
        val outcome = BlockInsertion.appendParagraph(blocks)
        val focusId = outcome.focusBlockId ?: return
        // Reusing the trailing empty paragraph never creates history.
        if (outcome.blocks !== blocks) recordHistory()
        blocks = outcome.blocks
        adoptFocus(focusId)
        scheduleSave()
    }

    private fun updateCurrentText(blockId: String, text: String) {
        blocks = blocks.map { block ->
            if (block.id != blockId) {
                block
            } else when (block) {
                is com.blackeye.kribo.data.blocks.TitleBlock -> block.copy(text = text)
                is ParagraphBlock -> block.copy(text = text)
                is com.blackeye.kribo.data.blocks.TaskBlock -> block.copy(text = text)
                is com.blackeye.kribo.data.blocks.QuoteBlock -> block.copy(text = text)
                else -> block
            }
        }
    }

    /** Keeps the legacy H1 that mirrors the title in lockstep (it is hidden from the list). */
    private fun syncDuplicateTitleBlock(previous: String, title: String) {
        val first = blocks.firstOrNull()
        if (first is com.blackeye.kribo.data.blocks.TitleBlock &&
            previous.isNotBlank() &&
            first.text.trim() == previous.trim()
        ) {
            blocks = blocks.mapIndexed { i, block ->
                if (i == 0) first.copy(text = title) else block
            }
        }
    }

    private fun adoptFocus(blockId: String) {
        focusedBlockId = blockId
        focusRequestId = blockId
        createdBlockId = blockId
        activeCaret = null
    }

    fun clearHighlight() {
        highlightQuery = ""
    }

    fun clearImageError() {
        imageError = false
    }

    fun updateBlock(blockId: String, transform: (NoteBlock) -> NoteBlock) {
        recordHistory(coalesce = blockId)
        blocks = blocks.map { block ->
            if (block.id == blockId) transform(block) else block
        }
        scheduleSave()
    }

    fun addBlock(block: NoteBlock) {
        applyToolbarAction(block, kindOf(block))
    }

    private fun kindOf(block: NoteBlock): BlockKind = when (block) {
        is com.blackeye.kribo.data.blocks.TitleBlock ->
            if (block.level <= 1) BlockKind.TITLE_1 else BlockKind.TITLE_2
        is ParagraphBlock -> BlockKind.PARAGRAPH
        is com.blackeye.kribo.data.blocks.TaskBlock -> BlockKind.TASK
        is com.blackeye.kribo.data.blocks.ToggleBlock -> BlockKind.TOGGLE
        is com.blackeye.kribo.data.blocks.QuoteBlock -> BlockKind.QUOTE
        is com.blackeye.kribo.data.blocks.LinkBlock -> BlockKind.LINK
        else -> BlockKind.PARAGRAPH
    }

    // ---------------------------------------------------------------------------------------
    // Reorder mode: one undo step per finished gesture (drag release, jump or "Move to…").
    // ---------------------------------------------------------------------------------------

    /** Clears text focus before the compact reorder list replaces the editor fields. */
    fun prepareForReorder() {
        focusedBlockId = null
        focusRequestId = null
        activeCaret = null
    }

    private var dragStartSnapshot: Snapshot? = null

    /** Snapshot taken on drag start; preview swaps do not touch history until release. */
    fun beginBlockDrag() {
        if (dragStartSnapshot == null) dragStartSnapshot = Snapshot(blocks, noteTitle)
    }

    /** Live swap while the finger moves; no history and no disk write yet. */
    fun previewMoveBlock(blockId: String, targetBlockId: String) {
        val from = blocks.indexOfFirst { it.id == blockId }
        val to = blocks.indexOfFirst { it.id == targetBlockId }
        if (from < 0 || to < 0 || from == to) return
        blocks = BlockReorder.moveBlock(blocks, from, to)
    }

    /** Commits the whole drag as a single undo step and persists it. */
    fun endBlockDrag() {
        val start = dragStartSnapshot ?: return
        dragStartSnapshot = null
        if (start.blocks == blocks) return
        undoStack.addLast(start)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        coalesceKey = null
        refreshHistoryFlags()
        scheduleSave(immediate = true)
    }

    /** [minIndex] keeps the legacy H1 that mirrors the note title pinned at the top. */
    fun moveBlockToTop(blockId: String, minIndex: Int = 0) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index <= minIndex || minIndex !in blocks.indices) return
        applyReorder(BlockReorder.moveBlock(blocks, index, minIndex))
    }

    fun moveBlockToBottom(blockId: String) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0 || index == blocks.lastIndex) return
        applyReorder(BlockReorder.moveBlockToBottom(blocks, index))
    }

    fun moveBlockRelativeTo(blockId: String, targetBlockId: String, placeBefore: Boolean) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        applyReorder(BlockReorder.moveBlockRelativeTo(blocks, index, targetBlockId, placeBefore))
    }

    fun moveBlockBy(blockId: String, delta: Int, minIndex: Int = 0) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val target = index + delta
        if (target !in blocks.indices || target < minIndex) return
        applyReorder(BlockReorder.moveBlock(blocks, index, target))
    }

    private fun applyReorder(updated: List<NoteBlock>) {
        if (updated === blocks || updated == blocks) return
        recordHistory()
        blocks = updated
        focusedBlockId = null
        focusRequestId = null
        activeCaret = null
        scheduleSave(immediate = true)
    }

    fun removeBlock(blockId: String) {
        recordHistory()
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
                    manual = true,
                )
                val anchor = pendingImageAnchor
                pendingImageAnchor = null
                val outcome = BlockInsertion.apply(
                    blocks = blocks,
                    activeId = anchor?.blockId ?: focusedBlockId,
                    caret = anchor?.caret ?: activeCaret,
                    newBlock = block,
                    kind = BlockKind.PHOTO,
                )
                recordHistory()
                val updated = outcome.blocks
                blocks = updated
                outcome.focusBlockId?.let { adoptFocus(it) }
                val loaded = withContext(Dispatchers.IO) {
                    repository.saveBlocks(noteId, updated)
                    repository.setNoteTitle(noteId, noteTitle)
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
        recordHistory(coalesce = blockId)
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

    /** Real text/block/title history: undo restores the last committed snapshot. */
    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snapshot(blocks, noteTitle))
        blocks = previous.blocks
        noteTitle = previous.title
        focusedBlockId = null
        focusRequestId = null
        activeCaret = null
        coalesceKey = null
        refreshHistoryFlags()
        scheduleSave(immediate = true)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snapshot(blocks, noteTitle))
        blocks = next.blocks
        noteTitle = next.title
        focusedBlockId = null
        focusRequestId = null
        activeCaret = null
        coalesceKey = null
        refreshHistoryFlags()
        scheduleSave(immediate = true)
    }

    /**
     * Snapshots the current blocks/title before a mutation. Consecutive keystrokes on the same
     * block (or in the title) collapse into one undo step so Ctrl+Z style recovery feels natural.
     */
    private fun recordHistory(coalesce: String? = null) {
        val now = System.currentTimeMillis()
        val merge = coalesce != null && coalesce == coalesceKey && now - coalesceAt < HISTORY_COALESCE_MS
        if (!merge) {
            undoStack.addLast(Snapshot(blocks, noteTitle))
            while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
        }
        coalesceKey = coalesce
        coalesceAt = now
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** Flushes the debounce and only then leaves the screen. No writes on the main thread. */
    fun flushAndExit(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persist(blocks, noteTitle)
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
            persist(blocks, noteTitle)
        }
    }

    private suspend fun persist(blocksSnapshot: List<NoteBlock>, titleSnapshot: String) {
        withContext(Dispatchers.IO) {
            repository.setNoteTitle(noteId, titleSnapshot)
            repository.setNoteFont(noteId, noteFont.name)
            repository.saveBlocks(noteId, blocksSnapshot)
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
        private const val MAX_HISTORY = 60
        private const val HISTORY_COALESCE_MS = 900L
        private const val TITLE_MAX_LENGTH = 120
        private const val TITLE_COALESCE_KEY = "title"
    }
}
