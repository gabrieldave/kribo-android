package com.blackeye.kribo.data.blocks

/** A brand-new note body (#38): one empty paragraph. The title already lives in the
 * note's title field, so no placeholder H1 is seeded next to it. */
fun newNoteBlocks(): List<NoteBlock> = listOf(ParagraphBlock())

/** Caret/selection inside the active text block, offsets clamped to the block text. */
data class Caret(val start: Int, val end: Int) {
    val hasSelection: Boolean get() = start != end
}

/** What a toolbar action means, independent of Compose: pure data for tests. */
enum class BlockKind {
    TITLE_1,
    TITLE_2,
    PARAGRAPH,
    ORDERED_LIST,
    BULLET_LIST,
    TASK,
    TOGGLE,
    QUOTE,
    LINK,
    PHOTO,
}

/**
 * Contextual block insertion (#37): convert-in-place, split at caret, or insert around the
 * active block. Deliberately free of Compose so the 7 toolbar buttons are unit-testable.
 *
 * Rules (see issue #37):
 * - blank active block → replaced by the chosen type in place;
 * - text selection → [before] + [new(selected)] + [after], `before`/`after` keep the original
 *   type; TOGGLE uses `selected` as its title; PHOTO splits the same way;
 * - single-line active block, different text type → converted in place, text kept;
 * - same type, TOGGLE, PHOTO, or multi-line text, no selection → caret decides:
 *   0 → insert before; middle → split [before] + [new] + [after as PARAGRAPH]; end → insert
 *   right after the active block (index + 1, never at the end of the note);
 * - no active block, or active block is an image/attachment → right after it or at the end.
 */
object BlockInsertion {

    data class Outcome(
        val blocks: List<NoteBlock>,
        /** Block that should receive focus (one-shot), null to keep the current focus. */
        val focusBlockId: String?,
        /** Caret offset to place at the end of the focused text, or null for end. */
        val focusCaret: Int? = null,
    )

    /** #39: double Enter inside a quote/toggle: trim the breaks, empty paragraph below. */
    fun doubleEnterExit(blocks: List<NoteBlock>, blockId: String): Outcome {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return Outcome(blocks, null)
        return when (val current = blocks[index]) {
            is QuoteBlock -> {
                val trimmed = current.text.trimEnd('\n')
                if (trimmed.isBlank()) {
                    val paragraph = ParagraphBlock()
                    Outcome(blocks.toMutableList().apply { this[index] = paragraph }, paragraph.id)
                } else {
                    val paragraph = ParagraphBlock()
                    Outcome(
                        blocks.toMutableList().apply {
                            this[index] = current.copy(text = trimmed)
                            add(index + 1, paragraph)
                        },
                        paragraph.id,
                    )
                }
            }
            is ToggleBlock -> {
                val trimmed = current.content.trimEnd('\n')
                val paragraph = ParagraphBlock()
                Outcome(
                    blocks.toMutableList().apply {
                        this[index] = current.copy(content = trimmed)
                        add(index + 1, paragraph)
                    },
                    paragraph.id,
                )
            }
            else -> Outcome(blocks, null)
        }
    }

    /** #39: Enter on a task: blank converts to a paragraph, non-blank adds a task below. */
    fun exitBelowTask(blocks: List<NoteBlock>, blockId: String): Outcome {
        val index = blocks.indexOfFirst { it.id == blockId }
        val current = blocks.getOrNull(index) as? TaskBlock ?: return Outcome(blocks, null)
        return if (current.text.isBlank()) {
            val paragraph = ParagraphBlock()
            Outcome(blocks.toMutableList().apply { this[index] = paragraph }, paragraph.id)
        } else {
            val task = TaskBlock()
            Outcome(blocks.toMutableList().apply { add(index + 1, task) }, task.id)
        }
    }

    /** Enter on a list item: blank converts to a paragraph, non-blank adds the next item below. */
    fun exitBelowList(blocks: List<NoteBlock>, blockId: String): Outcome {
        val index = blocks.indexOfFirst { it.id == blockId }
        val current = blocks.getOrNull(index) as? ListBlock ?: return Outcome(blocks, null)
        return if (current.text.isBlank()) {
            val paragraph = ParagraphBlock()
            Outcome(blocks.toMutableList().apply { this[index] = paragraph }, paragraph.id)
        } else {
            val next = ListBlock(ordered = current.ordered)
            Outcome(blocks.toMutableList().apply { add(index + 1, next) }, next.id)
        }
    }

    /**
     * Handles a typed or pasted newline inside a [ListBlock]:
     * - blank item → converts in place to [ParagraphBlock];
     * - Enter at end → appends a fresh [ListBlock] of the same ordered/bullet kind below;
     * - Enter in the middle / multi-line paste → splits into consecutive [ListBlock] items.
     */
    fun enterInList(blocks: List<NoteBlock>, blockId: String, valueWithNewline: String): Outcome {
        val index = blocks.indexOfFirst { it.id == blockId }
        val current = blocks.getOrNull(index) as? ListBlock ?: return Outcome(blocks, null)
        if (valueWithNewline.trim().isEmpty()) {
            val paragraph = ParagraphBlock()
            return Outcome(blocks.toMutableList().apply { this[index] = paragraph }, paragraph.id)
        }
        val lines = valueWithNewline.split('\n')
        val head = lines.first()
        val tail = lines.drop(1)
        val (headSpans, tailSpans) = SpanEditor.splitSpans(current.spans, head.length)
        val updated = blocks.toMutableList()
        updated[index] = current.copy(text = head, spans = headSpans)
        var focusId = current.id
        tail.forEachIndexed { offset, line ->
            val spans = if (offset == 0 && tail.size == 1) tailSpans else emptyList()
            val next = ListBlock(text = line, ordered = current.ordered, spans = spans)
            updated.add(index + 1 + offset, next)
            focusId = next.id
        }
        return Outcome(updated, focusId)
    }

    /** #39: bottom "New paragraph" row; a trailing empty paragraph is reused, not stacked. */
    fun appendParagraph(blocks: List<NoteBlock>): Outcome {
        val last = blocks.lastOrNull()
        if (last is ParagraphBlock && last.text.isBlank()) return Outcome(blocks, last.id)
        val paragraph = ParagraphBlock()
        return Outcome(blocks + paragraph, paragraph.id)
    }

    fun apply(
        blocks: List<NoteBlock>,
        activeId: String?,
        caret: Caret?,
        newBlock: NoteBlock,
        kind: BlockKind,
    ): Outcome {
        val activeIndex = blocks.indexOfFirst { it.id == activeId }
        if (activeIndex < 0) {
            return Outcome(blocks + newBlock, newBlock.id)
        }
        val active = blocks[activeIndex]

        if (active is AttachmentBlock || active is ToggleBlock || active is LinkBlock) {
            // Images have no caret; a collapsible section edits in two fields, so a toolbar
            // action simply inserts the new block right below it.
            return insertAt(blocks, activeIndex + 1, newBlock)
        }

        val text = textOf(active)
        val length = text.length
        val start = caret?.start?.coerceIn(0, length) ?: length
        val end = caret?.end?.coerceIn(start, length) ?: length

        if (text.isBlank() && kind != BlockKind.PHOTO) {
            // Empty block becomes the chosen type in place: no empty piles.
            return Outcome(
                blocks.toMutableList().apply { this[activeIndex] = ofKind(kind, newBlock, "") },
                newBlock.id,
            )
        }

        // Link button on URL-looking text: convert directly into a preloaded LinkBlock,
        // keeping the surrounding text as siblings when only part was selected.
        if (kind == BlockKind.LINK) {
            val candidate = when {
                start != end -> text.substring(start, end)
                else -> text
            }
            if (UrlSafety.looksLikeUrl(candidate)) {
                val normalized = UrlSafety.normalizeUrl(candidate) ?: candidate
                val link = LinkBlock(id = newBlock.id, url = normalized)
                if (start == end) {
                    return Outcome(
                        blocks.toMutableList().apply { this[activeIndex] = link },
                        link.id,
                    )
                }
                val before = text.substring(0, start)
                val after = text.substring(end)
                val parts = mutableListOf<NoteBlock>()
                if (before.isNotBlank()) parts += copyOf(active, before)
                parts += link
                if (after.isNotBlank()) parts += copyOf(active, after)
                return splice(blocks, activeIndex, parts, link.id)
            }
            // Not a URL: follow the caret insertion rules below (#37).
        }

        if (start != end) {
            val selected = text.substring(start, end)
            val before = text.substring(0, start)
            val after = text.substring(end)
            val selectedSpans = SpanEditor.splitSpans(
                SpanEditor.splitSpans(spansOf(active), end).first,
                start,
            ).second
            val parts = mutableListOf<NoteBlock>()
            if (before.isNotBlank()) parts += copyOf(active, before, start)
            val newPart = if (kind == BlockKind.TOGGLE) {
                ToggleBlock(id = newBlock.id, title = selected, expanded = true, titleSpans = selectedSpans)
            } else {
                ofKind(kind, newBlock, selected, selectedSpans)
            }
            parts += newPart
            if (after.isNotBlank()) {
                val afterSpans = SpanEditor.splitSpans(spansOf(active), end).second
                val afterBlock = copyOf(active, after, after.length)
                parts += when (afterBlock) {
                    is TitleBlock -> afterBlock.copy(spans = afterSpans)
                    is ParagraphBlock -> afterBlock.copy(spans = afterSpans)
                    is ListBlock -> afterBlock.copy(spans = afterSpans)
                    is TaskBlock -> afterBlock.copy(spans = afterSpans)
                    is QuoteBlock -> afterBlock.copy(spans = afterSpans)
                    else -> afterBlock
                }
            }
            return splice(blocks, activeIndex, parts, newPart.id)
        }

        val sameType = when (kind) {
            BlockKind.TITLE_1 -> active is TitleBlock && active.level <= 1
            BlockKind.TITLE_2 -> active is TitleBlock && active.level > 1
            BlockKind.PARAGRAPH -> active is ParagraphBlock
            BlockKind.ORDERED_LIST -> active is ListBlock && active.ordered
            BlockKind.BULLET_LIST -> active is ListBlock && !active.ordered
            BlockKind.TASK -> active is TaskBlock
            BlockKind.QUOTE -> active is QuoteBlock
            BlockKind.TOGGLE, BlockKind.LINK, BlockKind.PHOTO -> false
        }
        val singleLine = !text.contains('\n')

        if (singleLine && isTextKind(kind) && !sameType) {
            // One-tap conversion: the whole text (and inline spans) moves to the new type.
            return Outcome(
                blocks.toMutableList().apply {
                    this[activeIndex] = ofKind(kind, newBlock, text, spansOf(active))
                },
                newBlock.id,
                focusCaret = start,
            )
        }

        // Caret-driven insertion (multi-line text, same type, TOGGLE or PHOTO).
        return when {
            start == 0 -> insertAt(blocks, activeIndex, newBlock)
            start >= length -> insertAt(blocks, activeIndex + 1, newBlock)
            else -> {
                val before = text.substring(0, start).trimEnd('\n')
                val after = text.substring(start).trimStart('\n')
                val parts = mutableListOf<NoteBlock>()
                if (before.isNotBlank()) parts += copyOf(active, before, before.length)
                parts += newBlock
                if (after.isNotBlank()) {
                    parts += ParagraphBlock(text = after, spans = SpanEditor.splitSpans(spansOf(active), start).second)
                }
                splice(blocks, activeIndex, parts, newBlock.id)
            }
        }
    }

    private fun insertAt(
        blocks: List<NoteBlock>,
        index: Int,
        newBlock: NoteBlock,
    ): Outcome {
        val at = index.coerceIn(0, blocks.size)
        return Outcome(
            blocks.toMutableList().apply { add(at, newBlock) },
            newBlock.id,
        )
    }

    private fun splice(
        blocks: List<NoteBlock>,
        index: Int,
        parts: List<NoteBlock>,
        focusBlockId: String?,
    ): Outcome {
        val updated = blocks.toMutableList()
        updated.removeAt(index)
        updated.addAll(index, parts)
        return Outcome(updated, focusBlockId)
    }

    private fun isTextKind(kind: BlockKind): Boolean = when (kind) {
        BlockKind.TITLE_1,
        BlockKind.TITLE_2,
        BlockKind.PARAGRAPH,
        BlockKind.ORDERED_LIST,
        BlockKind.BULLET_LIST,
        BlockKind.TASK,
        BlockKind.QUOTE,
        -> true
        BlockKind.TOGGLE, BlockKind.LINK, BlockKind.PHOTO -> false
    }

    private fun spansOf(block: NoteBlock): List<InlineSpan> = when (block) {
        is TitleBlock -> block.spans
        is ParagraphBlock -> block.spans
        is ListBlock -> block.spans
        is TaskBlock -> block.spans
        is QuoteBlock -> block.spans
        is ToggleBlock -> block.titleSpans
        else -> emptyList()
    }

    private fun textOf(block: NoteBlock): String = when (block) {
        is TitleBlock -> block.text
        is ParagraphBlock -> block.text
        is ListBlock -> block.text
        is TaskBlock -> block.text
        is QuoteBlock -> block.text
        else -> ""
    }

    /**
     * Keeps the original block type (and its flags) with a substring of its text, as a sibling
     * block. [cut] marks how much of the source text the substring covers (span slicing).
     */
    private fun copyOf(block: NoteBlock, text: String, cut: Int = text.length): NoteBlock {
        val spans = SpanEditor.splitSpans(spansOf(block), cut).first
        return when (block) {
            is TitleBlock -> block.copy(id = BlockCodec.newId(), text = text, spans = spans)
            is ParagraphBlock -> block.copy(id = BlockCodec.newId(), text = text, spans = spans)
            is ListBlock -> block.copy(id = BlockCodec.newId(), text = text, spans = spans)
            is TaskBlock -> block.copy(id = BlockCodec.newId(), text = text, spans = spans)
            is QuoteBlock -> block.copy(id = BlockCodec.newId(), text = text, spans = spans)
            else -> ParagraphBlock(text = text)
        }
    }

    /** Builds the requested type from [template] (its id and extra fields stay). */
    private fun ofKind(
        kind: BlockKind,
        template: NoteBlock,
        text: String,
        spans: List<InlineSpan> = emptyList(),
    ): NoteBlock {
        val id = template.id
        return when (kind) {
            BlockKind.TITLE_1 -> TitleBlock(id = id, text = text, level = 1, spans = spans)
            BlockKind.TITLE_2 -> TitleBlock(id = id, text = text, level = 2, spans = spans)
            BlockKind.PARAGRAPH -> ParagraphBlock(id = id, text = text, spans = spans)
            BlockKind.ORDERED_LIST -> ListBlock(id = id, text = text, ordered = true, spans = spans)
            BlockKind.BULLET_LIST -> ListBlock(id = id, text = text, ordered = false, spans = spans)
            BlockKind.TASK -> TaskBlock(id = id, text = text, spans = spans)
            BlockKind.QUOTE -> QuoteBlock(id = id, text = text, spans = spans)
            BlockKind.TOGGLE -> ToggleBlock(id = id, title = text, expanded = true, titleSpans = spans)
            BlockKind.LINK -> LinkBlock(id = id, url = text)
            BlockKind.PHOTO -> template
        }
    }

    /**
     * Anti-giant-text paste (#38): splits pasted multi-line text into a single first line
     * (title / H1 / H2) plus the remainder as a plain paragraph.
     */
    data class PasteSplit(val head: String, val rest: String)

    fun splitTitlePaste(value: String): PasteSplit {
        if (!value.contains('\n')) return PasteSplit(value, "")
        val lines = value.split('\n')
        val headIndex = lines.indexOfFirst { it.isNotBlank() }
        if (headIndex < 0) return PasteSplit(value.trim(), "")
        val head = lines[headIndex].trim()
        val rest = lines.drop(headIndex + 1).joinToString("\n").trim()
        return PasteSplit(head, rest)
    }
}
