package com.blackeye.kribo.data.blocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockCodecTest {

    @Test
    fun roundTripsEveryBlockType() {
        val blocks: List<NoteBlock> = listOf(
            TitleBlock(text = "Título", level = 1),
            TitleBlock(text = "Subtítulo", level = 2),
            ParagraphBlock(text = "Párrafo con texto"),
            ListBlock(text = "Primer paso", ordered = true, spans = listOf(InlineSpan(0, 6, SpanType.BOLD))),
            ListBlock(text = "Viñeta", ordered = false),
            TaskBlock(text = "Comprar pan", checked = true),
            ToggleBlock(title = "Más info", content = "Contenido", expanded = true),
            QuoteBlock(text = "Cita"),
            AttachmentBlock(
                attachmentId = 7L,
                fileName = "scan.jpg",
                mimeType = "image/jpeg",
                previewText = "texto OCR",
                pageNumber = 2,
            ),
        )

        val encoded = BlockCodec.encode(blocks)
        val decoded = BlockCodec.decode(encoded)

        assertEquals(blocks.size, decoded.size)
        assertEquals(blocks.map { it::class }, decoded.map { it::class })
        assertEquals("Título", (decoded[0] as TitleBlock).text)
        assertEquals(true, (decoded[3] as ListBlock).ordered)
        assertEquals("Primer paso", (decoded[3] as ListBlock).text)
        assertEquals(1, (decoded[3] as ListBlock).spans.size)
        assertEquals(false, (decoded[4] as ListBlock).ordered)
        assertEquals(true, (decoded[5] as TaskBlock).checked)
        assertEquals(7L, (decoded[8] as AttachmentBlock).attachmentId)
        assertEquals("texto OCR", (decoded[8] as AttachmentBlock).previewText)
        assertEquals(2, (decoded[8] as AttachmentBlock).pageNumber)
        assertTrue(encoded.contains("\"type\":\"list\""))
        assertTrue(encoded.contains("\"ordered\":true"))
    }

    @Test
    fun orderedListIndexCountsConsecutiveOrderedItemsOnly() {
        val blocks: List<NoteBlock> = listOf(
            ListBlock(text = "Uno", ordered = true),
            ListBlock(text = "Dos", ordered = true),
            ListBlock(text = "Viñeta", ordered = false),
            ListBlock(text = "Reinicio", ordered = true),
            ParagraphBlock(text = "Texto"),
            ListBlock(text = "Otro", ordered = true),
        )

        assertEquals(1, orderedListIndex(blocks, 0))
        assertEquals(2, orderedListIndex(blocks, 1))
        assertEquals(0, orderedListIndex(blocks, 2))
        assertEquals(1, orderedListIndex(blocks, 3))
        assertEquals(0, orderedListIndex(blocks, 4))
        assertEquals(1, orderedListIndex(blocks, 5))

        val plain = BlockCodec.plainText(blocks)
        assertTrue(plain.contains("1. Uno\n2. Dos\n• Viñeta\n1. Reinicio"))
    }

    @Test
    fun olderAttachmentJsonWithoutPageNumberStillDecodes() {
        val legacy = """
            [{"type":"attachment","id":"a1","attachmentId":3,"fileName":"scan.jpg",
              "mimeType":"image/jpeg","previewText":"hola"}]
        """.trimIndent()

        val decoded = BlockCodec.decode(legacy)

        assertEquals(1, decoded.size)
        assertEquals(0, (decoded.first() as AttachmentBlock).pageNumber)
        assertEquals("hola", (decoded.first() as AttachmentBlock).previewText)
    }

    @Test
    fun derivedTitlePrefersTitleBlockThenFirstLine() {
        val withTitle = listOf(TitleBlock(text = "Mi título"), ParagraphBlock(text = "Cuerpo"))
        assertEquals("Mi título", BlockCodec.derivedTitle(withTitle))

        val withoutTitle = listOf(ParagraphBlock(text = "Primera línea\nSegunda"))
        assertEquals("Primera línea", BlockCodec.derivedTitle(withoutTitle))
    }

    @Test
    fun plainTextIndexesTasksAndAttachments() {
        val text = BlockCodec.plainText(
            listOf(
                ParagraphBlock(text = "Hola"),
                TaskBlock(text = "Pendiente", checked = false),
                TaskBlock(text = "Hecha", checked = true),
            ),
        )
        assertTrue(text.contains("Hola"))
        assertTrue(text.contains("[ ] Pendiente"))
        assertTrue(text.contains("[x] Hecha"))
    }

    @Test
    fun malformedJsonDegradesToEmptyList() {
        assertTrue(BlockCodec.decode("{not json}").isEmpty())
        assertTrue(BlockCodec.decode(null).isEmpty())
        assertTrue(BlockCodec.decode("[]").isEmpty())
    }

    @Test
    fun legacyContentBecomesOneParagraph() {
        val blocks = BlockCodec.legacyParagraphs("contenido v1")
        assertEquals(1, blocks.size)
        assertEquals("contenido v1", (blocks.first() as ParagraphBlock).text)
        assertTrue(BlockCodec.legacyParagraphs("").isEmpty())
    }
}
