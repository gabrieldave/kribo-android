package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import java.io.ByteArrayInputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EnexImporterTest {

    @Test
    fun importsTitleParagraphsTasksQuotesAndAttachments() = runBlocking {
        val enex = """
            <?xml version="1.0" encoding="UTF-8"?>
            <en-export>
              <note>
                <title>Recibo de luz</title>
                <content><![CDATA[<?xml version="1.0" encoding="UTF-8"?>
                  <en-note>
                    <div>Pago pendiente</div>
                    <div><en-todo checked="true"/>Pagar antes del viernes</div>
                    <div><en-todo checked="false"/>Guardar comprobante</div>
                    <blockquote>No olvidar el folio</blockquote>
                  </en-note>]]></content>
                <resource>
                  <data encoding="base64">${Base64.getEncoder().encodeToString("hola".toByteArray())}</data>
                  <mime>text/plain</mime>
                  <resource-attributes>
                    <file-name>recibo.txt</file-name>
                  </resource-attributes>
                </resource>
              </note>
            </en-export>
        """.trimIndent()

        val notes = EnexImporter().parseAll(ByteArrayInputStream(enex.toByteArray()))

        assertEquals(1, notes.size)
        val note = notes.first()
        assertEquals("Recibo de luz", note.title)
        assertTrue(note.blocks.any { it is ParagraphBlock && it.text.contains("Pago pendiente") })
        assertTrue(note.blocks.any { it is TaskBlock && it.text.contains("Pagar antes") && it.checked })
        assertFalse(note.blocks.filterIsInstance<TaskBlock>().first { it.text.contains("comprobante") }.checked)
        assertTrue(note.blocks.any { it is QuoteBlock && it.text.contains("folio") })
        assertEquals(1, note.attachments.size)
        assertEquals("recibo.txt", note.attachments.first().fileName)
        assertEquals("hola", String(note.attachments.first().bytes))
    }

    @Test
    fun streamsMultipleNotesIndependently() = runBlocking {
        val enex = """
            <en-export>
              <note><title>Uno</title><content><![CDATA[<en-note><div>A</div></en-note>]]></content></note>
              <note><title>Dos</title><content><![CDATA[<en-note><div>B</div></en-note>]]></content></note>
            </en-export>
        """.trimIndent()

        val notes = EnexImporter().parseAll(ByteArrayInputStream(enex.toByteArray()))

        assertEquals(2, notes.size)
        assertEquals(listOf("Uno", "Dos"), notes.map { it.title })
    }
}
