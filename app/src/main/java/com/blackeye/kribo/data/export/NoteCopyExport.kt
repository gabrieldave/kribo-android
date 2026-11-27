package com.blackeye.kribo.data.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.ListBlock
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import com.blackeye.kribo.data.blocks.orderedListIndex

private const val TITLE_DERIVED_MAX_LENGTH = 120
private const val MULTI_NOTE_SEPARATOR = "\n\n---\n\n"

private fun renderCopyBlock(
    blocks: List<NoteBlock>,
    index: Int,
    block: NoteBlock,
    ocrTexts: Map<Long, String>,
): String? = when (block) {
    is TitleBlock -> block.text.trim().takeIf { it.isNotEmpty() }
    is ParagraphBlock -> block.text.trim().takeIf { it.isNotEmpty() }
    is ListBlock -> block.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
        if (block.ordered) "${orderedListIndex(blocks, index)}. $text" else "• $text"
    }
    is QuoteBlock -> block.text.trim().takeIf { it.isNotEmpty() }
    is TaskBlock -> block.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
        (if (block.checked) "[x] " else "[ ] ") + text
    }
    is ToggleBlock -> buildString {
        if (block.title.isNotBlank()) append(block.title.trim())
        if (block.content.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(block.content.trim())
        }
    }.takeIf { it.isNotBlank() }
    is LinkBlock -> {
        val url = block.url.trim()
        val label = block.label.trim()
        when {
            url.isEmpty() && label.isEmpty() -> null
            url.isEmpty() -> label
            label.isEmpty() || label == url -> url
            else -> "$label ($url)"
        }
    }
    is AttachmentBlock -> {
        val ocr = block.attachmentId?.let { ocrTexts[it] }
            ?.takeIf { it.isNotBlank() }
            ?: block.previewText
        ocr.trim().takeIf { it.isNotEmpty() }
    }
}

/**
 * Formats a single note into clean plain text ready for the system clipboard: title (without
 * duplicating a matching leading headline/line), every block including collapsed toggles and
 * OCR text, and compact checklist lines.
 */
fun buildCopyNoteText(note: NoteExportPayload): String {
    val cleanTitle = note.title.trim()
    val rendered = note.blocks.mapIndexedNotNull { index, block ->
        renderCopyBlock(note.blocks, index, block, note.ocrTexts)?.let { text -> block to text }
    }
    if (rendered.isEmpty()) return cleanTitle

    val body = buildString {
        rendered.forEachIndexed { index, (block, text) ->
            if (index > 0) {
                val previous = rendered[index - 1].first
                val compactPair = (previous is TaskBlock && block is TaskBlock) ||
                    (previous is ListBlock && block is ListBlock && previous.ordered == block.ordered)
                append(if (compactPair) "\n" else "\n\n")
            }
            append(text)
        }
    }

    if (cleanTitle.isEmpty()) return body

    val (firstBlock, firstRendered) = rendered.first()
    val firstLine = firstRendered.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val derivedFirst = firstBlock.plainText()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
        .take(TITLE_DERIVED_MAX_LENGTH)

    val titleAlreadyFirst =
        (firstBlock is TitleBlock && firstBlock.text.trim() == cleanTitle) ||
            firstLine == cleanTitle ||
            (cleanTitle.length >= TITLE_DERIVED_MAX_LENGTH && firstLine.startsWith(cleanTitle)) ||
            derivedFirst == cleanTitle

    return if (titleAlreadyFirst) body else "$cleanTitle\n\n$body"
}

/** Joins one or several notes into clean clipboard text, skipping completely empty notes. */
fun buildCopyNotesText(notes: List<NoteExportPayload>): String = notes
    .map(::buildCopyNoteText)
    .filter { it.isNotBlank() }
    .joinToString(separator = MULTI_NOTE_SEPARATOR)

/** Writes [text] to the Android clipboard. Returns false if [text] is blank or clipboard fails. */
fun copyTextToClipboard(context: Context, label: String, text: String): Boolean {
    if (text.isBlank()) return false
    return runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    }.getOrDefault(false)
}
