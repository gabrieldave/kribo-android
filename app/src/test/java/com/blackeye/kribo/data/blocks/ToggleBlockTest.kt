package com.blackeye.kribo.data.blocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #34: new collapsible sections open immediately, saved sections keep their stored state. */
class ToggleBlockTest {

    @Test
    fun newSectionStartsExpandedSoTitleAndContentAreVisible() {
        val section = ToggleBlock.newSection()

        assertTrue(section.expanded)
        assertEquals("", section.title)
        assertEquals("", section.content)
    }

    @Test
    fun newSectionKeepsTheGivenTitleAndContent() {
        val section = ToggleBlock.newSection(title = "Detalles", content = "Texto interno")

        assertTrue(section.expanded)
        assertEquals("Detalles", section.title)
        assertEquals("Texto interno", section.content)
    }

    @Test
    fun dataClassDefaultStaysCollapsedForBackwardCompatibility() {
        assertFalse(ToggleBlock().expanded)
    }

    @Test
    fun storedSectionsRespectTheirSavedStateAfterRoundTrip() {
        val stored = listOf(
            ToggleBlock(title = "Abierta", content = "a", expanded = true),
            ToggleBlock(title = "Cerrada", content = "b", expanded = false),
        )

        val decoded = BlockCodec.decode(BlockCodec.encode(stored))

        assertEquals(2, decoded.size)
        assertTrue((decoded[0] as ToggleBlock).expanded)
        assertFalse((decoded[1] as ToggleBlock).expanded)
    }

    @Test
    fun legacyPayloadWithoutExpandedFlagDecodesCollapsed() {
        val legacy = """[{"type":"toggle","id":"t1","title":"Vieja","content":"x"}]"""

        val decoded = BlockCodec.decode(legacy)

        assertEquals(1, decoded.size)
        assertFalse((decoded.single() as ToggleBlock).expanded)
    }
}
