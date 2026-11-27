package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.blocks.ListBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownImporterTest {

    @Test
    fun convertsHeadingsTasksQuotesAndBullets() {
        val markdown = """
            # Mi nota

            Párrafo uno
            continúa aquí

            ## Subtítulo

            - [ ] Tarea pendiente
            - [x] Tarea hecha

            > Una cita importante

            - Un punto
            1. Paso uno
            2. Paso dos
            ![foto](imagen.png)
        """.trimIndent()

        val note = MarkdownImporter().parse(markdown, fallbackTitle = "archivo")

        assertEquals("Mi nota", note.title)
        assertEquals(TitleBlock::class, note.blocks[0]::class)
        assertEquals("Mi nota", (note.blocks[0] as TitleBlock).text)
        assertEquals(1, (note.blocks[0] as TitleBlock).level)

        val paragraph = note.blocks.filterIsInstance<ParagraphBlock>().first()
        assertEquals("Párrafo uno\ncontinúa aquí", paragraph.text)

        val subtitle = note.blocks.filterIsInstance<TitleBlock>()[1]
        assertEquals("Subtítulo", subtitle.text)
        assertEquals(2, subtitle.level)

        val tasks = note.blocks.filterIsInstance<TaskBlock>()
        assertEquals(2, tasks.size)
        assertEquals("Tarea pendiente", tasks[0].text)
        assertEquals(false, tasks[0].checked)
        assertEquals(true, tasks[1].checked)

        val quote = note.blocks.filterIsInstance<QuoteBlock>().first()
        assertEquals("Una cita importante", quote.text)

        val lists = note.blocks.filterIsInstance<ListBlock>()
        assertEquals(3, lists.size)
        assertEquals(ListBlock(id = lists[0].id, text = "Un punto", ordered = false), lists[0])
        assertEquals(ListBlock(id = lists[1].id, text = "Paso uno", ordered = true), lists[1])
        assertEquals(ListBlock(id = lists[2].id, text = "Paso dos", ordered = true), lists[2])
        assertTrue(note.blocks.any { it is ParagraphBlock && it.text == "Imagen: foto" })
    }

    @Test
    fun zipCreatesFoldersFromTopLevelDirectories() = runBlocking {
        val zipBytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                fun add(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                add("Proyecto/A.md", "# A\n\nTexto A")
                add("Proyecto/B.md", "# B\n\nTexto B")
                add("Suelta.md", "# Suelta")
                add("Proyecto/imagen.png", "not-a-note")
            }
        }.toByteArray()

        val notes = mutableListOf<ImportedNote>()
        val count = MarkdownImporter().parseZip(ByteArrayInputStream(zipBytes)) { notes += it }

        assertEquals(3, count)
        assertEquals("Proyecto", notes[0].folderName)
        assertEquals("Proyecto", notes[1].folderName)
        assertNull(notes[2].folderName)
        assertEquals("Suelta", notes[2].title)
    }

    @Test
    fun zipExtractsReferencedImagesAndKeepsThemInsideTheNote() = runBlocking {
        val zipBytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                fun addBytes(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                addBytes(
                    "Notas/A.md",
                    "# A\n\nPárrafo\n\n![foto](assets/foto.png)\n\nDespués".toByteArray(),
                )
                addBytes("Notas/assets/foto.png", byteArrayOf(1, 2, 3, 4))
                addBytes(
                    "Notas/B.md",
                    "# B\n\n![[assets/dos.jpg]]\n".toByteArray(),
                )
                addBytes("Notas/assets/dos.jpg", byteArrayOf(9, 8, 7))
            }
        }.toByteArray()

        val notes = mutableListOf<ImportedNote>()
        MarkdownImporter().parseZip(ByteArrayInputStream(zipBytes)) { notes += it }

        assertEquals(2, notes.size)
        val first = notes.first { it.title == "A" }
        assertEquals(1, first.attachments.size)
        assertEquals("foto.png", first.attachments.first().fileName)
        assertEquals("image/png", first.attachments.first().mimeType)
        assertTrue(first.blocks.any { it is com.blackeye.kribo.data.blocks.AttachmentBlock && it.fileName == "foto.png" })
        assertTrue(first.blocks.none { it is ParagraphBlock && it.text.startsWith("Imagen:") })

        val second = notes.first { it.title == "B" }
        assertEquals("dos.jpg", second.attachments.single().fileName)
        assertEquals("image/jpeg", second.attachments.single().mimeType)
    }

    @Test
    fun stripsYamlFrontMatter() {
        val markdown = """
            ---
            title: Ignorado
            tags: [a, b]
            ---
            # Real
            Cuerpo
        """.trimIndent()

        val note = MarkdownImporter().parse(markdown)
        assertEquals("Real", note.title)
        assertTrue(note.blocks.none { it is ParagraphBlock && it.text.contains("tags:") })
    }
}
