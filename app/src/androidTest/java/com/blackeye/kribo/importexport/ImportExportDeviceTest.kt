package com.blackeye.kribo.importexport

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.seedDefaultFolders
import com.blackeye.kribo.ocr.AttachmentStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level proof of the free import/export pipeline, folder CRUD and PDF generation. */
@RunWith(AndroidJUnit4::class)
class ImportExportDeviceTest {

    @Test
    fun importsMarkdownZipEnexAndExportsMarkdownPdfAndBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "import-export-${System.currentTimeMillis()}")
        val database = Room.inMemoryDatabaseBuilder(context, KriboDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedDefaultFolders(database.openHelper.writableDatabase)

        val repository = NoteRepository(database, root)
        val store = AttachmentStore(root)
        val importer = ImportService(repository, store)
        val exporter = ExportService(repository)

        // Markdown single file.
        val markdown = "# Nota importada\n\nCuerpo del documento\n\n- [x] Tarea hecha\n"
        val mdSummary = importer.import(
            "nota.md",
            ByteArrayInputStream(markdown.toByteArray()),
            defaultFolderId = 1L,
        )
        assertEquals(1, mdSummary.notes)

        // ZIP with subfolders.
        val zipBytes = zipOf(
            "Proyecto/Plan.md" to "# Plan\n\nPaso uno",
            "Proyecto/Ideas.md" to "# Ideas\n\n- [ ] Explorar",
            "Raiz.md" to "# Raiz",
        )
        val zipSummary = importer.import(
            "paquete.zip",
            ByteArrayInputStream(zipBytes),
            defaultFolderId = 1L,
        )
        assertEquals(3, zipSummary.notes)
        assertNotNull("ZIP subfolder must be created", database.folderDao().findByName("Proyecto"))

        // ENEX with a real attachment.
        val enex = """
            <en-export>
              <note>
                <title>Recibo escaneado</title>
                <content><![CDATA[<en-note><div>Total 120</div></en-note>]]></content>
                <resource>
                  <data encoding="base64">${Base64.getEncoder().encodeToString("adjunto".toByteArray())}</data>
                  <mime>text/plain</mime>
                  <resource-attributes><file-name>recibo.txt</file-name></resource-attributes>
                </resource>
              </note>
            </en-export>
        """.trimIndent()
        val enexSummary = importer.import(
            "recibo.enex",
            ByteArrayInputStream(enex.toByteArray()),
            defaultFolderId = 1L,
        )
        assertEquals(1, enexSummary.notes)
        assertEquals(1, enexSummary.attachments)

        val notes = repository.getAllNotes()
        assertTrue(notes.size >= 5)
        val receipt = notes.first { it.title.contains("Recibo") }
        val attachments = repository.getAttachments(receipt.id)
        assertEquals(1, attachments.size)
        assertTrue(File(attachments.first().filePath).isFile)
        assertEquals("adjunto", File(attachments.first().filePath).readText())

        // Markdown export.
        val mdFile = exporter.exportNoteMarkdown(receipt.id, File(root, "exports/recibo.md"))
        assertTrue(mdFile.readText().contains("# Recibo escaneado"))
        assertTrue(mdFile.readText().contains("Total 120"))

        // PDF export.
        val pdfFile = exporter.exportNotePdf(receipt.id, File(root, "exports/recibo.pdf"))
        assertTrue("PDF should have content", pdfFile.length() > 1_000)
        val header = pdfFile.inputStream().use { it.readNBytes(5) }
        assertEquals("%PDF-", String(header))

        // Full backup as standard .md inside a .zip.
        val backup = File(root, "backup.zip")
        backup.outputStream().use { output ->
            exporter.writeBackup(output)
        }
        var markdownEntries = 0
        ZipInputStream(backup.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".md")) markdownEntries++
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        assertTrue("Backup must contain markdown files", markdownEntries >= 5)

        // Folder CRUD keeps notes in General when a folder is deleted.
        val folderId = repository.createFolder("Temporal")
        assertNotNull(folderId)
        assertTrue(repository.renameFolder(folderId!!, "Renombrada"))
        val noteId = receipt.id
        repository.moveToFolder(noteId, folderId)
        assertEquals(folderId, repository.getNote(noteId)?.folderId)
        assertTrue(repository.deleteFolder(folderId))
        assertEquals(1L, repository.getNote(noteId)?.folderId)

        database.close()
        root.deleteRecursively()
        Unit
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }
}
