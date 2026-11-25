package com.blackeye.kribo.data.blocks

import com.blackeye.kribo.importexport.renderMarkdownSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #42: inline span engine — toggles, overlaps, typing adjusts, splits, legacy JSON. */
class SpanEditorTest {

    private fun b(s: Int, e: Int) = InlineSpan(s, e, SpanType.BOLD)
    private fun c(s: Int, e: Int, t: AccentTone) = InlineSpan(s, e, SpanType.TEXT_COLOR, t)
    private fun h(s: Int, e: Int, t: AccentTone) = InlineSpan(s, e, SpanType.HIGHLIGHT, t)

    @Test
    fun togglesBoldOnAndOff() {
        val on = SpanEditor.toggleStyle(emptyList(), 0, 5, SpanType.BOLD, textLength = 10)
        assertEquals(listOf(b(0, 5)), on)

        val off = SpanEditor.toggleStyle(on, 0, 5, SpanType.BOLD, textLength = 10)
        assertEquals(emptyList<InlineSpan>(), off)
    }

    @Test
    fun partialCoveredCompletesInsteadOfClearing() {
        val half = SpanEditor.toggleStyle(listOf(b(0, 3)), 0, 6, SpanType.BOLD, textLength = 10)

        val byStart = half.map { it.start }.sorted()
        assertEquals(0, byStart.first())
        // whole 0..6 covered afterwards
        assertEquals(ActiveStyles(bold = true), SpanEditor.activeStylesAt(half, 1, 1))
        assertEquals(ActiveStyles(bold = true), SpanEditor.activeStylesAt(half, 5, 5))
    }

    @Test
    fun toggleOffSlicesOnlyTheSelection() {
        val spans = listOf(b(0, 10))
        val out = SpanEditor.toggleStyle(spans, 3, 6, SpanType.BOLD, textLength = 10)

        assertEquals(listOf(b(0, 3), b(6, 10)), out)
    }

    @Test
    fun textColorReplacesToneAndNullClears() {
        val red = SpanEditor.toggleStyle(emptyList(), 0, 5, SpanType.TEXT_COLOR, AccentTone.CORAL)
        val blue = SpanEditor.toggleStyle(red, 2, 4, SpanType.TEXT_COLOR, AccentTone.CYAN)
        assertEquals(
            listOf(c(0, 2, AccentTone.CORAL), c(2, 4, AccentTone.CYAN), c(4, 5, AccentTone.CORAL)),
            blue,
        )

        val cleared = SpanEditor.toggleStyle(blue, 1, 3, SpanType.TEXT_COLOR, null)
        assertEquals(
            listOf(c(0, 1, AccentTone.CORAL), c(3, 4, AccentTone.CYAN), c(4, 5, AccentTone.CORAL)),
            cleared,
        )
    }

    @Test
    fun highlightStacksWithTextColor() {
        val spans = listOf(
            c(0, 5, AccentTone.AMBER),
            h(0, 5, AccentTone.PURPLE),
        )

        val active = SpanEditor.activeStylesAt(spans, 2, 2)

        assertEquals(AccentTone.AMBER, active.textTone)
        assertEquals(AccentTone.PURPLE, active.highlightTone)
    }

    @Test
    fun clearFormattingSelectionAndWholeBlock() {
        val spans = listOf(b(0, 5), c(3, 8, AccentTone.AMBER))
        val partial = SpanEditor.clearFormatting(spans, 2, 4)
        assertEquals(listOf(b(0, 2), c(4, 8, AccentTone.AMBER), b(4, 5)), partial)

        assertEquals(
            emptyList<InlineSpan>(),
            SpanEditor.clearFormatting(spans, 0, 0),
        )
    }

    @Test
    fun typingInsideBoldRangeKeepsStyle() {
        val spans = listOf(b(0, 5))
        val out = SpanEditor.adjustSpansOnEdit("hola.", "holax.", spans, ActiveStyles())

        assertEquals(listOf(b(0, 6)), out)
    }

    @Test
    fun typingBoldWithActiveStyleAtCaret() {
        val out = SpanEditor.adjustSpansOnEdit("hola", "holax", emptyList(), ActiveStyles(bold = true))

        assertEquals(listOf(b(4, 5)), out)
    }

    @Test
    fun deletingMiddleClipsSpans() {
        val spans = listOf(b(0, 4), c(6, 9, AccentTone.EMERALD))
        val out = SpanEditor.adjustSpansOnEdit("hola mundo", "hola", spans, ActiveStyles())

        assertEquals(listOf(b(0, 4)), out)
    }

    @Test
    fun splitSpansRebasesRightSide() {
        val spans = listOf(b(0, 4), c(6, 9, AccentTone.CYAN))
        val (left, right) = SpanEditor.splitSpans(spans, 6)

        assertEquals(listOf(b(0, 4)), left)
        assertEquals(listOf(c(0, 3, AccentTone.CYAN)), right)
    }

    @Test
    fun splittingANewSpanCutsItInTwo() {
        val (left, right) = SpanEditor.splitSpans(listOf(b(0, 10)), 4)

        assertEquals(listOf(b(0, 4)), left)
        assertEquals(listOf(b(0, 6)), right)
    }

    @Test
    fun adjacentEqualRangesMerge() {
        val merged = SpanEditor.normalize(listOf(b(0, 3), b(3, 5)))

        assertEquals(listOf(b(0, 5)), merged)
    }

    @Test
    fun legacyJsonWithoutSpansDecodesWithDefaults() {
        val legacy = """[{"type":"paragraph","id":"p1","text":"Hola"}]"""
        val legacyTask = """[{"type":"task","id":"t1","text":"Tarea","checked":true}]"""
        val legacyToggle = """[{"type":"toggle","id":"g1","title":"Sec","content":"Texto","expanded":true}]"""

        val paragraph = BlockCodec.decode(legacy).single() as ParagraphBlock
        assertEquals("Hola", paragraph.text)
        assertTrue(paragraph.spans.isEmpty())

        val task = BlockCodec.decode(legacyTask).single() as TaskBlock
        assertTrue(task.checked)
        assertTrue(task.spans.isEmpty())

        val toggle = BlockCodec.decode(legacyToggle).single() as ToggleBlock
        assertTrue(toggle.expanded)
        assertTrue(toggle.contentSpans.isEmpty())
    }

    @Test
    fun spansRoundTripThroughJson() {
        val block = ParagraphBlock(
            text = "Hola mundo",
            spans = listOf(b(0, 4), c(5, 10, AccentTone.CORAL)),
        )
        val decoded = BlockCodec.decode(BlockCodec.encode(listOf(block)))

        val restored = decoded.single() as ParagraphBlock
        assertEquals("Hola mundo", restored.text)
        assertEquals(listOf(b(0, 4), c(5, 10, AccentTone.CORAL)), restored.spans)
    }

    @Test
    fun typingManyCharsWithActiveStylesGrowsOneRange() {
        // Simulates Gboard-style per-keystroke commits with B+I+U+S+color+highlight armed.
        var text = ""
        var spans = emptyList<InlineSpan>()
        val active = ActiveStyles(
            bold = true,
            italic = true,
            underline = true,
            strikethrough = true,
            textTone = AccentTone.CORAL,
            highlightTone = AccentTone.AMBER,
        )
        "PruebaEstilo".forEach { char ->
            val next = text + char
            spans = SpanEditor.adjustSpansOnEdit(text, next, spans, active)
            text = next
        }

        assertEquals(12, text.length)
        // Every character got every style: ranges overlap fully on [0, 12).
        spans.filter { it.type == SpanType.BOLD }.forEach {
            assertEquals(0, it.start); assertEquals(12, it.end)
        }
        assertEquals(AccentTone.CORAL, SpanEditor.activeStylesAt(spans, 5, 5).textTone)
        assertEquals(AccentTone.AMBER, SpanEditor.activeStylesAt(spans, 5, 5).highlightTone)
        assertTrue(SpanEditor.activeStylesAt(spans, 5, 5).bold)
        assertTrue(SpanEditor.activeStylesAt(spans, 5, 5).italic)
        assertTrue(SpanEditor.activeStylesAt(spans, 5, 5).underline)
        assertTrue(SpanEditor.activeStylesAt(spans, 5, 5).strikethrough)
    }

    @Test
    fun removingStyleWhileTypingKeepsPreviousCharsStyled() {
        val spans = listOf(b(0, 5), InlineSpan(5, 10, SpanType.ITALIC))
        val out = SpanEditor.adjustSpansOnEdit("hola mundo", "hola mundox", spans, ActiveStyles())

        assertTrue(SpanEditor.activeStylesAt(out, 1, 1).bold)
        val x = SpanEditor.activeStylesAt(out, 11, 11)
        assertTrue("new char must be plain", !x.bold && !x.italic)
    }

    @Test
    fun markdownRendersEmphasis() {
        val spans = listOf(
            b(0, 4),
            InlineSpan(5, 10, SpanType.ITALIC),
            InlineSpan(11, 15, SpanType.STRIKETHROUGH),
            InlineSpan(16, 20, SpanType.UNDERLINE),
        )
        val markdown = renderMarkdownSpans("Hola mundo feliz fin!", spans)

        assertEquals("**Hola** *mundo* ~~feli~~z<u> fin</u>!", markdown)
    }
}
