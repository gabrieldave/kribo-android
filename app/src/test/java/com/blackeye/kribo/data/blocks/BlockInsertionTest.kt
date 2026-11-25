package com.blackeye.kribo.data.blocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #37 + #38: contextual toolbar insertion, one-tap conversion, caret splits and
 * anti-giant-text paste handling. Pure logic, no Compose.
 */
class BlockInsertionTest {

    private fun h1(text: String, level: Int = 1) =
        TitleBlock(id = "h1", text = text, level = level)

    @Test
    fun giantH1PlusParagraphButtonConvertsInPlace() {
        val blocks = listOf(h1("Texto extraído enorme"))
        val paragraph = ParagraphBlock()

        val outcome = BlockInsertion.apply(blocks, "h1", Caret(3, 3), paragraph, BlockKind.PARAGRAPH)

        assertEquals(1, outcome.blocks.size)
        val converted = outcome.blocks.single() as ParagraphBlock
        assertEquals("Texto extraído enorme", converted.text)
        assertEquals(paragraph.id, outcome.focusBlockId)
    }

    @Test
    fun quotePlusParagraphConvertsFully() {
        val blocks = listOf(QuoteBlock(id = "q", text = "citare"))
        val paragraph = ParagraphBlock()

        val outcome = BlockInsertion.apply(blocks, "q", null, paragraph, BlockKind.PARAGRAPH)

        assertEquals(1, outcome.blocks.size)
        assertEquals("citare", (outcome.blocks.single() as ParagraphBlock).text)
    }

    @Test
    fun shortSingleLineBlockConvertsToRequestedType() {
        val blocks = listOf(ParagraphBlock(id = "p", text = "comprar pan"))

        val title = TitleBlock(level = 1)
        val outcomeH1 = BlockInsertion.apply(blocks, "p", Caret(2, 2), title, BlockKind.TITLE_1)
        assertEquals(1, outcomeH1.blocks.size)
        assertEquals("comprar pan", (outcomeH1.blocks.single() as TitleBlock).text)
        assertEquals(1, (outcomeH1.blocks.single() as TitleBlock).level)

        val task = TaskBlock()
        val outcomeTask = BlockInsertion.apply(blocks, "p", Caret(0, 0), task, BlockKind.TASK)
        assertEquals(1, outcomeTask.blocks.size)
        assertEquals("comprar pan", (outcomeTask.blocks.single() as TaskBlock).text)
    }

    @Test
    fun caretAtStartInsertsAbove() {
        val blocks = listOf(
            ParagraphBlock(id = "a", text = "uno"),
            ParagraphBlock(id = "b", text = "dos dos dos"),
        )

        // Same type (Paragraph → Paragraph): only the caret rule applies, 0 → above.
        val paragraph = ParagraphBlock()
        val outcome = BlockInsertion.apply(blocks, "b", Caret(0, 0), paragraph, BlockKind.PARAGRAPH)

        assertEquals(3, outcome.blocks.size)
        assertEquals("a", outcome.blocks[0].id)
        assertEquals(paragraph.id, outcome.blocks[1].id)
        assertEquals("b", outcome.blocks[2].id)
    }

    @Test
    fun caretInMiddleSplitsIntoBeforeNewAndAfter() {
        val blocks = listOf(ParagraphBlock(id = "p", text = "antes medio después"))
        val paragraph = ParagraphBlock()

        val outcome = BlockInsertion.apply(blocks, "p", Caret(6, 6), paragraph, BlockKind.PARAGRAPH)

        assertEquals(3, outcome.blocks.size)
        assertEquals("antes ", (outcome.blocks[0] as ParagraphBlock).text)
        assertTrue(outcome.blocks[1] is ParagraphBlock)
        assertEquals("medio después", (outcome.blocks[2] as ParagraphBlock).text)
        assertEquals(paragraph.id, outcome.focusBlockId)
    }

    @Test
    fun caretAtEndInsertsImmediatelyAfterActiveBlock() {
        val blocks = listOf(
            ParagraphBlock(id = "a", text = "primero"),
            ParagraphBlock(id = "b", text = "segundo"),
            ParagraphBlock(id = "c", text = "tercero"),
        )

        val paragraph = ParagraphBlock()
        val outcome = BlockInsertion.apply(blocks, "a", Caret(8, 8), paragraph, BlockKind.PARAGRAPH)

        assertEquals(4, outcome.blocks.size)
        assertEquals("a", outcome.blocks[0].id)
        assertEquals(paragraph.id, outcome.blocks[1].id)
        assertEquals("b", outcome.blocks[2].id)
    }

    @Test
    fun selectionSplitsIntoBeforeNewAndAfterKeepingTypes() {
        val blocks = listOf(ParagraphBlock(id = "p", text = "hola mundo cruel"))
        val task = TaskBlock()

        val outcome = BlockInsertion.apply(blocks, "p", Caret(5, 10), task, BlockKind.TASK)

        assertEquals(3, outcome.blocks.size)
        assertEquals("hola ", (outcome.blocks[0] as ParagraphBlock).text)
        assertEquals("mundo", (outcome.blocks[1] as TaskBlock).text)
        assertEquals(" cruel", (outcome.blocks[2] as ParagraphBlock).text)
    }

    @Test
    fun selectionBecomesToggleTitleWithParagraphAround() {
        val blocks = listOf(h1("ver esto luego"))
        val section = ToggleBlock.newSection()

        val outcome = BlockInsertion.apply(blocks, "h1", Caret(4, 8), section, BlockKind.TOGGLE)

        assertEquals(3, outcome.blocks.size)
        assertEquals("ver ", (outcome.blocks[0] as TitleBlock).text)
        val toggle = outcome.blocks[1] as ToggleBlock
        assertEquals("esto", toggle.title)
        assertTrue(toggle.expanded)
        assertEquals(" luego", (outcome.blocks[2] as TitleBlock).text)
    }

    @Test
    fun toggleFollowsCaretPlacementToo() {
        val blocks = listOf(ParagraphBlock(id = "a", text = "uno"))
        val section = ToggleBlock.newSection()

        // Cursor at 0 → the section is inserted above the active block (#37 rule 5).
        val outcome = BlockInsertion.apply(blocks, "a", Caret(0, 0), section, BlockKind.TOGGLE)

        assertEquals(2, outcome.blocks.size)
        assertEquals(section.id, outcome.blocks[0].id)
        assertEquals("a", outcome.blocks[1].id)
        assertTrue((outcome.blocks[0] as ToggleBlock).expanded)
    }

    @Test
    fun blankActiveBlockIsReplacedInPlace() {
        val blocks = listOf(
            ParagraphBlock(id = "a", text = "uno"),
            ParagraphBlock(id = "empty", text = ""),
            ParagraphBlock(id = "b", text = "dos"),
        )
        val quote = QuoteBlock()

        val outcome = BlockInsertion.apply(blocks, "empty", Caret(0, 0), quote, BlockKind.QUOTE)

        assertEquals(3, outcome.blocks.size)
        assertEquals("a", outcome.blocks[0].id)
        assertTrue(outcome.blocks[1] is QuoteBlock)
        assertEquals("b", outcome.blocks[2].id)
    }

    @Test
    fun withoutActiveBlockAppendsAtEnd() {
        val blocks = listOf(ParagraphBlock(id = "a", text = "uno"))
        val task = TaskBlock()

        val outcome = BlockInsertion.apply(blocks, null, null, task, BlockKind.TASK)

        assertEquals(2, outcome.blocks.size)
        assertEquals(task.id, outcome.blocks.last().id)
    }

    @Test
    fun photoSplitsParagraphAtCaret() {
        val blocks = listOf(ParagraphBlock(id = "p", text = "izq der"))
        val photo = AttachmentBlock(attachmentId = 7L, fileName = "a.jpg", mimeType = "image/jpeg")

        val outcome = BlockInsertion.apply(blocks, "p", Caret(4, 4), photo, BlockKind.PHOTO)

        assertEquals(3, outcome.blocks.size)
        assertEquals("izq ", (outcome.blocks[0] as ParagraphBlock).text)
        assertEquals(7L, (outcome.blocks[1] as AttachmentBlock).attachmentId)
        assertEquals("der", (outcome.blocks[2] as ParagraphBlock).text)
    }

    @Test
    fun multilinePasteKeepsFirstLineAndDropsRestAsParagraph() {
        val split = BlockInsertion.splitTitlePaste("Recibo de luz\nLuz 42.10\nAgua 8.20")

        assertEquals("Recibo de luz", split.head)
        assertEquals("Luz 42.10\nAgua 8.20", split.rest)
    }

    @Test
    fun singleLinePasteIsUnchanged() {
        val split = BlockInsertion.splitTitlePaste("Solo título")

        assertEquals("Solo título", split.head)
        assertTrue(split.rest.isBlank())
    }

    @Test
    fun urlSelectionConvertsToLinkBlock() {
        val blocks = listOf(ParagraphBlock(id = "p", text = "https://www.example.com/recibo"))
        val link = LinkBlock()

        val outcome = BlockInsertion.apply(blocks, "p", Caret(0, 30), link, BlockKind.LINK)

        assertEquals(1, outcome.blocks.size)
        val converted = outcome.blocks.single() as LinkBlock
        assertEquals("https://www.example.com/recibo", converted.url)
        assertEquals(link.id, outcome.focusBlockId)
    }

    @Test
    fun linkButtonOnPlainTextFollowsCaretRules() {
        val blocks = listOf(ParagraphBlock(id = "a", text = "uno"))
        val link = LinkBlock()

        val outcome = BlockInsertion.apply(blocks, "a", Caret(3, 3), link, BlockKind.LINK)

        assertEquals(2, outcome.blocks.size)
        assertEquals(link.id, outcome.blocks[1].id)
    }

    @Test
    fun normalizeUrlOnlyAllowsHttpAndHttps() {
        assertEquals("https://www.example.com", UrlSafety.normalizeUrl("www.example.com"))
        assertEquals("http://example.com/a", UrlSafety.normalizeUrl("http://example.com/a"))
        assertEquals("https://example.com/a", UrlSafety.normalizeUrl("  https://example.com/a "))
        assertEquals(null, UrlSafety.normalizeUrl("javascript:alert(1)"))
        assertEquals(null, UrlSafety.normalizeUrl("file:///etc/passwd"))
        assertEquals(null, UrlSafety.normalizeUrl("content://com.app/file"))
        assertEquals(null, UrlSafety.normalizeUrl("intent://scan#Intent;scheme=https;end"))
        assertEquals(null, UrlSafety.normalizeUrl("not a url"))
    }

    @Test
    fun unsafeSchemesNeverLookLikeUrls() {
        assertFalse(UrlSafety.looksLikeUrl("javascript:alert(1)"))
        assertFalse(UrlSafety.looksLikeUrl("file:///x"))
        assertTrue(UrlSafety.looksLikeUrl("www.example.com"))
        assertTrue(UrlSafety.looksLikeUrl("https://example.com"))
    }

    @Test
    fun extractSafeUrlsCapsAtThreeUniqueAndTrimsPunctuation() {
        val text = "Mira esto. https://a.com/x, otro https://b.com/y https://a.com/x https://c.com https://d.com"
        val urls = UrlSafety.extractSafeUrls(text)

        assertEquals(listOf("https://a.com/x", "https://b.com/y", "https://c.com"), urls)
    }

    @Test
    fun linkBlockRoundTripsJsonAndDefaultsLabel() {
        val block = LinkBlock(url = "https://example.com", label = "Enviar")
        val decoded = BlockCodec.decode(BlockCodec.encode(listOf(block)))

        assertEquals(1, decoded.size)
        val link = decoded.single() as LinkBlock
        assertEquals("https://example.com", link.url)
        assertEquals("Enviar", link.label)
        assertEquals("Enviar https://example.com", link.plainText())

        val legacy = """[{"type":"link","id":"l1","url":"https://example.com"}]"""
        val legacyDecoded = BlockCodec.decode(legacy)
        assertEquals("", (legacyDecoded.single() as LinkBlock).label)
    }

    @Test
    fun doubleEnterInQuoteExitsToParagraphBelowKeepingText() {
        val blocks = listOf(QuoteBlock(id = "q", text = "citare\n"))

        val outcome = BlockInsertion.doubleEnterExit(blocks, "q")

        assertEquals(2, outcome.blocks.size)
        assertEquals("citare", (outcome.blocks[0] as QuoteBlock).text)
        assertTrue(outcome.blocks[1] is ParagraphBlock)
        assertEquals("", (outcome.blocks[1] as ParagraphBlock).text)
        assertEquals(outcome.blocks[1].id, outcome.focusBlockId)
    }

    @Test
    fun doubleEnterInEmptyQuoteConvertsItInPlace() {
        val blocks = listOf(ParagraphBlock(id = "a", text = "x"), QuoteBlock(id = "q", text = "\n"))

        val outcome = BlockInsertion.doubleEnterExit(blocks, "q")

        assertEquals(2, outcome.blocks.size)
        assertTrue(outcome.blocks[1] is ParagraphBlock)
        assertEquals(outcome.blocks[1].id, outcome.focusBlockId)
    }

    @Test
    fun doubleEnterInToggleContentExitsBelowToggle() {
        val blocks = listOf(
            ToggleBlock(id = "t", title = "Sec", content = "dentro\n", expanded = true),
        )

        val outcome = BlockInsertion.doubleEnterExit(blocks, "t")

        assertEquals(2, outcome.blocks.size)
        val toggle = outcome.blocks[0] as ToggleBlock
        assertEquals("dentro", toggle.content)
        assertTrue(outcome.blocks[1] is ParagraphBlock)
    }

    @Test
    fun enterOnBlankTaskConvertsToParagraph() {
        val blocks = listOf(
            TaskBlock(id = "a", text = "una"),
            TaskBlock(id = "t", text = ""),
        )

        val outcome = BlockInsertion.exitBelowTask(blocks, "t")

        assertEquals(2, outcome.blocks.size)
        assertEquals("a", outcome.blocks[0].id)
        assertTrue(outcome.blocks[1] is ParagraphBlock)
        assertEquals(outcome.blocks[1].id, outcome.focusBlockId)
    }

    @Test
    fun enterOnTaskCreatesTaskBelow() {
        val blocks = listOf(TaskBlock(id = "t", text = "hecha"))

        val outcome = BlockInsertion.exitBelowTask(blocks, "t")

        assertEquals(2, outcome.blocks.size)
        assertTrue(outcome.blocks[1] is TaskBlock)
        assertEquals("", (outcome.blocks[1] as TaskBlock).text)
    }

    @Test
    fun appendParagraphNeverDuplicatesATrailingEmptyParagraph() {
        val blocks = listOf(ParagraphBlock(id = "a", text = "uno"), ParagraphBlock(id = "e", text = ""))

        val outcome = BlockInsertion.appendParagraph(blocks)

        assertEquals(blocks, outcome.blocks)
        assertEquals("e", outcome.focusBlockId)
    }

    @Test
    fun appendParagraphAddsOneWhenLastIsNotEmpty() {
        val blocks = listOf(TaskBlock(id = "t", text = "uno"))

        val outcome = BlockInsertion.appendParagraph(blocks)

        assertEquals(2, outcome.blocks.size)
        assertTrue(outcome.blocks.last() is ParagraphBlock)
    }

    @Test
    fun freshNoteStartsWithOnlyAParagraph() {
        val blocks = newNoteBlocks()

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is ParagraphBlock)
        assertEquals(0, blocks.count { it is TitleBlock })
    }

    @Test
    fun splitIdsAreUniqueAfterSplit() {
        val blocks = listOf(ParagraphBlock(id = "p", text = "hola mundo"))
        val task = TaskBlock()

        val outcome = BlockInsertion.apply(blocks, "p", Caret(5, 10), task, BlockKind.TASK)

        val ids = outcome.blocks.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(ids.contains("p"))
    }
}
