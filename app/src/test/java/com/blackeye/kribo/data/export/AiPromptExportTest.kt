package com.blackeye.kribo.data.export

import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptExportTest {

    private fun fullNote() = NoteExportPayload(
        title = "Plan de lanzamiento",
        folder = "General",
        blocks = listOf(
            TitleBlock(text = "Plan de lanzamiento"),
            TitleBlock(text = "Objetivos de la semana", level = 2),
            ParagraphBlock(text = "Mantener notas rápidas, privadas y listas sin internet."),
            QuoteBlock(text = "La claridad supera a la velocidad."),
            LinkBlock(url = "https://kribo.iareal.net"),
            TaskBlock(text = "Revisar escáner OCR offline", checked = true),
            TaskBlock(text = "Publicar versión en Google Play", checked = false),
            AttachmentBlock(
                attachmentId = 7L,
                fileName = "recibo.jpg",
                previewText = "recibo",
            ),
        ),
        ocrTexts = mapOf(7L to "Total: 42,00 €"),
    )

    @Test
    fun singleNoteIncludesTitleFolderAndEveryCleanBlock() {
        val text = buildCombinedNotesText(listOf(fullNote()), isSpanish = true)

        assertTrue(text.contains("Título: Plan de lanzamiento"))
        assertTrue(text.contains("Carpeta: General"))
        assertTrue(text.contains("Objetivos de la semana"))
        assertTrue(text.contains("Mantener notas rápidas, privadas y listas sin internet."))
        assertTrue(text.contains("> La claridad supera a la velocidad."))
        assertTrue(text.contains("https://kribo.iareal.net"))
        assertTrue(text.contains("[x] Revisar escáner OCR offline"))
        assertTrue(text.contains("[ ] Publicar versión en Google Play"))
        assertTrue(text.contains("Total: 42,00 €"))
        // A single note is never wrapped in the multi-note separators.
        assertFalse(text.contains("=== NOTA 1"))
    }

    @Test
    fun threeNotesAreCombinedWithHeaderAndSeparators() {
        val notes = listOf(
            fullNote(),
            fullNote().copy(title = "Ideas", folder = "IA"),
            fullNote().copy(title = "Recibos", folder = "Recibos"),
        )
        val text = buildCombinedNotesText(notes, isSpanish = false)

        assertTrue(text.startsWith("Combined notes: 3"))
        assertTrue(text.contains("=== NOTE 1: Plan de lanzamiento (General) ==="))
        assertTrue(text.contains("=== NOTE 2: Ideas (IA) ==="))
        assertTrue(text.contains("=== NOTE 3: Recibos (Recibos) ==="))
        assertEquals(3, Regex("=== NOTE ").findAll(text).count())
    }

    @Test
    fun spanishCombinedHeaderIsLocalized() {
        val notes = listOf(fullNote(), fullNote().copy(title = "Otra"))
        val text = buildCombinedNotesText(notes, isSpanish = true)

        assertTrue(text.startsWith("Notas combinadas: 2"))
        assertTrue(text.contains("=== NOTA 1: Plan de lanzamiento (General) ==="))
        assertTrue(text.contains("=== NOTA 2: Otra (General) ==="))
    }

    @Test
    fun shareFileNameSanitizesSingleTitle() {
        val note = fullNote().copy(title = "Plan de lanzamiento: 2026 / Q1")
        assertEquals(
            "Kribo_Plan-de-lanzamiento-2026-Q1.txt",
            buildShareFileName(listOf(note), isSpanish = true),
        )
    }

    @Test
    fun shareFileNameUsesCountWhenCombining() {
        val notes = List(3) { fullNote().copy(title = "Nota $it") }
        assertEquals("Kribo_3_notas.txt", buildShareFileName(notes, isSpanish = true))
        assertEquals("Kribo_3_notes.txt", buildShareFileName(notes, isSpanish = false))
        assertEquals(
            "Kribo_nota.txt",
            buildShareFileName(listOf(fullNote().copy(title = "   ")), isSpanish = true),
        )
    }

    @Test
    fun fourPresetsExistInBothLanguagesAndDiffer() {
        AiPreset.entries.forEach { preset ->
            val english = preset.prompt(isSpanish = false)
            val spanish = preset.prompt(isSpanish = true)
            assertTrue("Empty English prompt for $preset", english.isNotBlank())
            assertTrue("Empty Spanish prompt for $preset", spanish.isNotBlank())
            assertFalse("$preset should not reuse the same text", english == spanish)
        }
        // The four prompts must be distinct inside each language.
        assertEquals(4, AiPreset.entries.map { it.prompt(false) }.toSet().size)
        assertEquals(4, AiPreset.entries.map { it.prompt(true) }.toSet().size)
    }

    @Test
    fun presetsMentionTheirDeliverable() {
        assertTrue(AiPreset.SUMMARY.prompt(false).contains("executive summary"))
        assertTrue(AiPreset.SUMMARY.prompt(true).contains("resumen ejecutivo"))
        assertTrue(AiPreset.INFOGRAPHIC.prompt(false).contains("infographic"))
        assertTrue(AiPreset.INFOGRAPHIC.prompt(true).contains("infografía"))
        assertTrue(AiPreset.HTML.prompt(false).contains(".html"))
        assertTrue(AiPreset.ACTION_PLAN.prompt(false).contains("priority"))
        assertTrue(AiPreset.ACTION_PLAN.prompt(true).contains("prioridad"))
    }
}
