package com.blackeye.kribo.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun matchesWithoutAccentsOrCase() {
        val text = "La canción de abril"
        val matches = SearchText.matches(text, "CANCION")

        assertEquals(1, matches.size)
        assertEquals("canción", text.substring(matches[0].start, matches[0].end))
        assertTrue(SearchText.contains("Página 2", "pagina"))
        assertFalse(SearchText.contains("Página 2", "factura"))
    }

    @Test
    fun findsEveryOccurrenceInOriginalCoordinates() {
        val text = "Cancion, CANCION y canción."
        val matches = SearchText.matches(text, "cancion")

        assertEquals(3, matches.size)
        assertEquals(listOf("Cancion", "CANCION", "canción"), matches.map {
            text.substring(it.start, it.end)
        })
    }

    @Test
    fun snippetAlwaysKeepsTheMatchedWord() {
        val source = "Relleno ".repeat(20) + "el recibo de la luz llegó ayer" + " más relleno".repeat(20)
        val snippet = SearchText.snippet(source, "recibo")

        assertTrue(snippet.contains("recibo"))
        assertTrue(snippet.length < source.length)
    }

    @Test
    fun snippetCentresOnLateMatchWithoutCuttingIt() {
        val source = "x".repeat(500) + " comprobante " + "y".repeat(500)
        val snippet = SearchText.snippet(source, "comprobante")

        assertTrue(snippet.contains("comprobante"))
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
    }

    @Test
    fun highlightProducesAnnotatedSpans() {
        val style = SpanStyle(color = Color.Cyan)
        val annotated = SearchText.highlight("Factura de luz", "luz", style)

        assertEquals("Factura de luz", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
        assertEquals(11, annotated.spanStyles.first().start)
        assertEquals(14, annotated.spanStyles.first().end)
    }

    @Test
    fun blankQueryNeverMatchesOrHighlights() {
        assertTrue(SearchText.matches("texto", "").isEmpty())
        assertTrue(SearchText.highlight("texto", "   ", SpanStyle()).spanStyles.isEmpty())
    }
}
