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
            TaskBlock(text = "Comprar pan", checked = true),
            ToggleBlock(title = "Más info", content = "Contenido", expanded = true),
            QuoteBlock(text = "Cita"),
            AttachmentBlock(
                attachmentId = 7L,
                fileName = "scan.jpg",
                mimeType = "image/jpeg",
                previewText = "texto OCR",
            ),
        )

        val encoded = BlockCodec.encode(blocks)
        val decoded = BlockCodec.decode(encoded)

        assertEquals(blocks.size, decoded.size)
        assertEquals(blocks.map { it::class }, decoded.map { it::class })
        assertEquals("Título", (decoded[0] as TitleBlock).text)
        assertEquals(true, (decoded[3] as TaskBlock).checked)
        assertEquals(7L, (decoded[6] as AttachmentBlock).attachmentId)
        assertEquals("texto OCR", (decoded[6] as AttachmentBlock).previewText)
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
