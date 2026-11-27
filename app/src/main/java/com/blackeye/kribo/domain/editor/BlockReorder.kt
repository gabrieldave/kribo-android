package com.blackeye.kribo.domain.editor

import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.ListBlock
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import com.blackeye.kribo.ui.common.SearchText

/**
 * Pure reordering engine for the block editor.
 *
 * Every function returns a **new** list (or the same instance when the move is a no-op) so the
 * ViewModel can compare references and record exactly one undo step per user gesture.
 */
object BlockReorder {

    fun moveBlock(blocks: List<NoteBlock>, fromIndex: Int, toIndex: Int): List<NoteBlock> {
        if (fromIndex !in blocks.indices || toIndex !in blocks.indices) return blocks
        if (fromIndex == toIndex) return blocks
        val result = blocks.toMutableList()
        val moving = result.removeAt(fromIndex)
        result.add(toIndex, moving)
        return result
    }

    fun moveBlockToTop(blocks: List<NoteBlock>, fromIndex: Int): List<NoteBlock> =
        moveBlock(blocks, fromIndex, 0)

    fun moveBlockToBottom(blocks: List<NoteBlock>, fromIndex: Int): List<NoteBlock> =
        if (fromIndex !in blocks.indices) blocks else moveBlock(blocks, fromIndex, blocks.lastIndex)

    /**
     * Places the block at [fromIndex] immediately before or after [targetBlockId].
     *
     * The target is located **after** removing the moving block, so indices stay correct when
     * the source sits above the destination (`fromIndex < targetIndex`).
     */
    fun moveBlockRelativeTo(
        blocks: List<NoteBlock>,
        fromIndex: Int,
        targetBlockId: String,
        placeBefore: Boolean,
    ): List<NoteBlock> {
        if (fromIndex !in blocks.indices) return blocks
        val moving = blocks[fromIndex]
        if (moving.id == targetBlockId) return blocks
        if (blocks.none { it.id == targetBlockId }) return blocks

        val without = blocks.toMutableList().apply { removeAt(fromIndex) }
        val targetIndex = without.indexOfFirst { it.id == targetBlockId }
        if (targetIndex < 0) return blocks
        val insertAt = if (placeBefore) targetIndex else targetIndex + 1
        without.add(insertAt.coerceIn(0, without.size), moving)
        return without
    }

    /**
     * Accent/case-insensitive filter over every visible fragment of a block. The original
     * position is preserved in [IndexedValue.index] so the UI can show `#14` and move by the
     * real index in the note, even while a search filter is active.
     */
    fun filterBlocksForSearch(
        blocks: List<NoteBlock>,
        query: String,
    ): List<IndexedValue<NoteBlock>> {
        val needle = query.trim()
        if (needle.isEmpty()) return blocks.withIndex().toList()
        return blocks.withIndex().filter { (_, block) ->
            searchableText(block).any { SearchText.contains(it, needle) }
        }
    }

    private fun searchableText(block: NoteBlock): List<String> = when (block) {
        is TitleBlock -> listOf(block.text)
        is ParagraphBlock -> listOf(block.text)
        is ListBlock -> listOf(block.text)
        is QuoteBlock -> listOf(block.text)
        is TaskBlock -> listOf(block.text)
        is ToggleBlock -> listOf(block.title, block.content)
        is LinkBlock -> listOf(block.label, block.url)
        is AttachmentBlock -> listOf(block.fileName, block.previewText)
    }

    /**
     * Visual-only shortening for the compact link card: drops the scheme and `www.` prefix and
     * a single trailing slash. The stored URL is never modified.
     */
    fun formatCompactDisplayUrl(url: String): String {
        var value = url.trim()
        value = value.removePrefixIgnoreCase("https://")
        value = value.removePrefixIgnoreCase("http://")
        value = value.removePrefixIgnoreCase("www.")
        if (value.length > 1 && value.endsWith("/")) value = value.dropLast(1)
        return value
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
