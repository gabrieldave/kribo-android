package com.blackeye.kribo.data.export

import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.ListBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCopyExportTest {

    @Test
    fun singleNoteCopiesCleanTitleAndAllBlockTypesWithoutMetadataHeaders() {
        val note = NoteExportPayload(
            title = "Lee el Repo",
            folder = "General",
            blocks = listOf(
                TitleBlock(text = "Instrucciones", level = 2),
                ParagraphBlock(text = "Empieza por las instrucciones y el README."),
                ListBlock(text = "Leer guía", ordered = true),
                ListBlock(text = "Ejecutar pruebas", ordered = true),
                ListBlock(text = "Punto clave", ordered = false),
                TaskBlock(text = "Revisar arquitectura", checked = true),
                TaskBlock(text = "Correr pruebas", checked = false),
                ToggleBlock(
                    title = "Detalles técnicos",
                    content = "Contenido oculto dentro del desplegable.",
                    expanded = false,
                ),
                QuoteBlock(text = "La claridad supera a la velocidad."),
                LinkBlock(url = "https://kribo.iareal.net", label = "Sitio web"),
                LinkBlock(url = "https://example.com"),
                AttachmentBlock(attachmentId = 7L, fileName = "recibo.jpg", previewText = "preview"),
                AttachmentBlock(attachmentId = 8L, fileName = "foto.jpg", previewText = "", manual = true),
            ),
            ocrTexts = mapOf(7L to "Total: 42,00 €"),
        )

        val copied = buildCopyNoteText(note)

        val expected = listOf(
            "Lee el Repo",
            "Instrucciones",
            "Empieza por las instrucciones y el README.",
            "1. Leer guía\n2. Ejecutar pruebas",
            "• Punto clave",
            "[x] Revisar arquitectura\n[ ] Correr pruebas",
            "Detalles técnicos\nContenido oculto dentro del desplegable.",
            "La claridad supera a la velocidad.",
            "Sitio web (https://kribo.iareal.net)",
            "https://example.com",
            "Total: 42,00 €",
        ).joinToString(separator = "\n\n")

        assertEquals(expected, copied)
        assertFalse(copied.contains("Título:"))
        assertFalse(copied.contains("Carpeta:"))
        assertFalse(copied.contains("foto.jpg"))
    }

    @Test
    fun legacyDuplicateTitleBlockIsNotRepeated() {
        val note = NoteExportPayload(
            title = "Plan de lanzamiento",
            folder = "General",
            blocks = listOf(
                TitleBlock(text = "Plan de lanzamiento", level = 1),
                ParagraphBlock(text = "Primer paso del plan."),
            ),
        )

        assertEquals("Plan de lanzamiento\n\nPrimer paso del plan.", buildCopyNoteText(note))
    }

    @Test
    fun quickCaptureDerivedTitleIsNotRepeated() {
        val note = NoteExportPayload(
            title = "Comprar leche",
            folder = "General",
            blocks = listOf(
                ParagraphBlock(text = "Comprar leche\nComprar pan"),
            ),
        )

        assertEquals("Comprar leche\nComprar pan", buildCopyNoteText(note))
    }

    @Test
    fun quickCaptureLongTruncatedTitleIsNotRepeated() {
        val longLine = "A".repeat(150)
        val note = NoteExportPayload(
            title = longLine.take(120),
            folder = "General",
            blocks = listOf(
                ParagraphBlock(text = "$longLine\nSegunda línea"),
            ),
        )

        assertEquals("$longLine\nSegunda línea", buildCopyNoteText(note))
    }

    @Test
    fun shortTitlePrefixOfFirstParagraphIsKept() {
        val note = NoteExportPayload(
            title = "Lee",
            folder = "General",
            blocks = listOf(
                ParagraphBlock(text = "Leer el repo completo."),
            ),
        )

        assertEquals("Lee\n\nLeer el repo completo.", buildCopyNoteText(note))
    }

    @Test
    fun toggleDerivedTitleIsNotRepeated() {
        val note = NoteExportPayload(
            title = "Sección Contenido interno",
            folder = "General",
            blocks = listOf(
                ToggleBlock(title = "Sección", content = "Contenido interno", expanded = false),
            ),
        )

        assertEquals("Sección\nContenido interno", buildCopyNoteText(note))
    }

    @Test
    fun titleOnlyAndEmptyNotesAreHandledGracefully() {
        val titleOnly = NoteExportPayload(
            title = "Recordatorio",
            folder = "General",
            blocks = listOf(ParagraphBlock(text = "   ")),
        )
        assertEquals("Recordatorio", buildCopyNoteText(titleOnly))

        val empty = NoteExportPayload(
            title = "   ",
            folder = "General",
            blocks = listOf(ParagraphBlock(text = "")),
        )
        assertEquals("", buildCopyNoteText(empty))
    }

    @Test
    fun multipleSelectedNotesJoinWithSeparatorAndSkipEmpty() {
        val notes = listOf(
            NoteExportPayload(
                title = "Primera",
                folder = "General",
                blocks = listOf(ParagraphBlock(text = "Texto 1")),
            ),
            NoteExportPayload(
                title = "",
                folder = "General",
                blocks = emptyList(),
            ),
            NoteExportPayload(
                title = "Segunda",
                folder = "Trabajo",
                blocks = listOf(ParagraphBlock(text = "Texto 2")),
            ),
        )

        val copied = buildCopyNotesText(notes)
        assertEquals("Primera\n\nTexto 1\n\n---\n\nSegunda\n\nTexto 2", copied)
        assertTrue(copied.contains("---"))
    }
}
