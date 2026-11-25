package com.blackeye.kribo.domain.editor

import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure reorder engine (#44): index math, destination search and the compact link label.
 * No Compose, no database.
 */
class BlockReorderTest {

    private fun sample(): List<NoteBlock> = listOf(
        ParagraphBlock(id = "a", text = "Uno"),
        TitleBlock(id = "b", text = "Objetivos", level = 2),
        ParagraphBlock(id = "c", text = "Dos"),
        LinkBlock(id = "d", url = "https://kribo.iareal.net", label = ""),
        TaskBlock(id = "e", text = "Publicar versión"),
        ToggleBlock(id = "f", title = "Detalles", content = "Contenido oculto"),
        AttachmentBlock(id = "g", fileName = "recibo.jpg", previewText = "Total 120 pesos"),
    )

    private fun ids(blocks: List<NoteBlock>): List<String> = blocks.map { it.id }

    @Test
    fun moveBlockMovesLastToFirstAndFirstToLast() {
        val blocks = sample()

        assertEquals(
            listOf("g", "a", "b", "c", "d", "e", "f"),
            ids(BlockReorder.moveBlock(blocks, blocks.lastIndex, 0)),
        )
        assertEquals(
            listOf("b", "c", "d", "e", "f", "g", "a"),
            ids(BlockReorder.moveBlock(blocks, 0, blocks.lastIndex)),
        )
    }

    @Test
    fun moveBlockOutOfRangeAndSameIndexAreNoOps() {
        val blocks = sample()

        assertSame(blocks, BlockReorder.moveBlock(blocks, -1, 2))
        assertSame(blocks, BlockReorder.moveBlock(blocks, 2, 99))
        assertSame(blocks, BlockReorder.moveBlock(blocks, 3, 3))
    }

    @Test
    fun moveBlockToTopAndBottom() {
        val blocks = sample()

        assertEquals(
            listOf("c", "a", "b", "d", "e", "f", "g"),
            ids(BlockReorder.moveBlockToTop(blocks, 2)),
        )
        assertEquals(
            listOf("a", "c", "d", "e", "f", "g", "b"),
            ids(BlockReorder.moveBlockToBottom(blocks, 1)),
        )
        assertSame(blocks, BlockReorder.moveBlockToTop(blocks, 0))
        assertSame(blocks, BlockReorder.moveBlockToBottom(blocks, blocks.lastIndex))
        assertSame(blocks, BlockReorder.moveBlockToTop(blocks, 99))
    }

    @Test
    fun moveRelativeBeforeWhenSourceIsAboveTarget() {
        val blocks = sample()

        val moved = BlockReorder.moveBlockRelativeTo(
            blocks = blocks,
            fromIndex = 0,
            targetBlockId = "d",
            placeBefore = true,
        )

        assertEquals(listOf("b", "c", "a", "d", "e", "f", "g"), ids(moved))
    }

    @Test
    fun moveRelativeAfterWhenSourceIsAboveTarget() {
        val blocks = sample()

        val moved = BlockReorder.moveBlockRelativeTo(
            blocks = blocks,
            fromIndex = 0,
            targetBlockId = "b",
            placeBefore = false,
        )

        assertEquals(listOf("b", "a", "c", "d", "e", "f", "g"), ids(moved))
    }

    @Test
    fun moveRelativeBeforeWhenSourceIsBelowTarget() {
        val blocks = sample()

        val moved = BlockReorder.moveBlockRelativeTo(
            blocks = blocks,
            fromIndex = 6,
            targetBlockId = "b",
            placeBefore = true,
        )

        assertEquals(listOf("a", "g", "b", "c", "d", "e", "f"), ids(moved))
    }

    @Test
    fun moveRelativeAfterWhenSourceIsBelowTarget() {
        val blocks = sample()

        val moved = BlockReorder.moveBlockRelativeTo(
            blocks = blocks,
            fromIndex = 6,
            targetBlockId = "b",
            placeBefore = false,
        )

        assertEquals(listOf("a", "b", "g", "c", "d", "e", "f"), ids(moved))
    }

    @Test
    fun moveRelativeIgnoresSelfAndUnknownTargets() {
        val blocks = sample()

        assertSame(
            blocks,
            BlockReorder.moveBlockRelativeTo(blocks, 2, "c", placeBefore = true),
        )
        assertSame(
            blocks,
            BlockReorder.moveBlockRelativeTo(blocks, 2, "missing", placeBefore = true),
        )
        assertSame(
            blocks,
            BlockReorder.moveBlockRelativeTo(blocks, -1, "b", placeBefore = true),
        )
    }

    @Test
    fun searchFindsTextLinkToggleAndOcrKeepingOriginalIndex() {
        val blocks = sample()

        val title = BlockReorder.filterBlocksForSearch(blocks, "objetivos")
        assertEquals(listOf(1), title.map { it.index })
        assertEquals("b", title.single().value.id)

        val link = BlockReorder.filterBlocksForSearch(blocks, "kribo.iareal")
        assertEquals(listOf(3), link.map { it.index })

        val toggleContent = BlockReorder.filterBlocksForSearch(blocks, "oculto")
        assertEquals(listOf(5), toggleContent.map { it.index })

        val ocr = BlockReorder.filterBlocksForSearch(blocks, "120 pesos")
        assertEquals(listOf(6), ocr.map { it.index })
        assertTrue(ocr.single().value is AttachmentBlock)
    }

    @Test
    fun searchIsAccentInsensitiveAndBlankReturnsEverything() {
        val blocks = listOf(
            ParagraphBlock(id = "a", text = "Revisar escáner OCR"),
            TaskBlock(id = "b", text = "Publicar"),
        )

        assertEquals(
            listOf(0),
            BlockReorder.filterBlocksForSearch(blocks, "escaner").map { it.index },
        )
        assertEquals(
            listOf(0, 1),
            BlockReorder.filterBlocksForSearch(blocks, "   ").map { it.index },
        )
        assertTrue(BlockReorder.filterBlocksForSearch(blocks, "inexistente").isEmpty())
    }

    @Test
    fun compactDisplayUrlDropsSchemeWwwAndTrailingSlash() {
        assertEquals(
            "kribo.iareal.net",
            BlockReorder.formatCompactDisplayUrl("https://kribo.iareal.net/"),
        )
        assertEquals(
            "example.com/guia",
            BlockReorder.formatCompactDisplayUrl("http://www.example.com/guia"),
        )
        assertEquals(
            "kribo.app",
            BlockReorder.formatCompactDisplayUrl("HTTPS://WWW.kribo.app/"),
        )
        assertEquals(
            "kribo.iareal.net",
            BlockReorder.formatCompactDisplayUrl("kribo.iareal.net"),
        )
    }
}
