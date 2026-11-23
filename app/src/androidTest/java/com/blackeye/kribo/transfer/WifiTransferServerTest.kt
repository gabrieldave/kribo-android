package com.blackeye.kribo.transfer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.seedDefaultFolders
import com.blackeye.kribo.importexport.ExportService
import com.blackeye.kribo.ocr.AttachmentStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Socket
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WifiTransferServerTest {

    private lateinit var database: KriboDatabase
    private lateinit var repository: NoteRepository
    private lateinit var server: WifiTransferServer
    private lateinit var root: File

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "wifi-test-${System.currentTimeMillis()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, KriboDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedDefaultFolders(database.openHelper.writableDatabase)
        repository = NoteRepository(database, root)
        server = WifiTransferServer(context, repository, ExportService(repository))
    }

    @After
    fun tearDown() {
        server.close()
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun pin401NotesPostExportAndBackupWork() = runBlocking {
        assertTrue(server.start(0))
        val port = server.state.value.port
        val pin = server.state.value.pin
        assertTrue(port > 0)
        assertEquals(6, pin.length)

        val unauthorized = request(port, "GET", "/api/notes")
        assertEquals(401, unauthorized.status)

        val authed = request(port, "GET", "/api/notes", pin = pin)
        assertEquals(200, authed.status)
        assertTrue(authed.bodyText.contains("["))

        val created = request(
            port,
            "POST",
            "/api/notes",
            pin = pin,
            body = """{"title":"Desde el navegador","text":"Hola desde la computadora"}""",
            contentType = "application/json",
        )
        assertEquals(200, created.status)
        val id = Regex("\"id\":(\\d+)").find(created.bodyText)!!.groupValues[1].toLong()

        val listed = request(port, "GET", "/api/notes?q=navegador", pin = pin)
        assertEquals(200, listed.status)
        assertTrue(listed.bodyText.contains("Desde el navegador"))

        val export = request(port, "GET", "/api/notes/$id/export", pin = pin)
        assertEquals(200, export.status)
        assertTrue(export.bodyText.contains("Hola desde la computadora"))

        val attachmentFile = File(root, "photo.txt").apply { writeText("adjunto") }
        val attachmentId = repository.addAttachment(
            noteId = id,
            filePath = attachmentFile.absolutePath,
            mimeType = "text/plain",
            extractedText = "texto OCR",
        )
        val attachment = request(port, "GET", "/api/attachments/$attachmentId", pin = pin)
        assertEquals(200, attachment.status)
        assertEquals("adjunto", attachment.bodyText)

        val backup = request(port, "GET", "/api/backup.zip", pin = pin)
        assertEquals(200, backup.status)
        val entries = ZipInputStream(backup.body.inputStream()).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
        assertTrue(entries.any { it.endsWith(".md") })
        assertTrue(entries.any { it.startsWith("attachments/") })

        server.stop()
        assertFalse(server.state.value.running)
    }

    private data class Response(val status: Int, val body: ByteArray) {
        val bodyText: String get() = body.toString(Charsets.UTF_8)
    }

    private fun request(
        port: Int,
        method: String,
        path: String,
        pin: String? = null,
        body: String = "",
        contentType: String = "text/plain; charset=utf-8",
    ): Response {
        val bytes = body.toByteArray(Charsets.UTF_8)
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10_000
            val output = socket.getOutputStream()
            val headers = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1:$port\r\n")
                append("Connection: close\r\n")
                if (pin != null) append("X-Kribo-Pin: $pin\r\n")
                if (bytes.isNotEmpty()) {
                    append("Content-Type: $contentType\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                }
                append("\r\n")
            }
            output.write(headers.toByteArray(Charsets.UTF_8))
            if (bytes.isNotEmpty()) output.write(bytes)
            output.flush()

            val raw = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = socket.getInputStream().read(buffer)
                if (read < 0) break
                raw.write(buffer, 0, read)
            }
            val all = raw.toByteArray()
            val separator = indexOfHeaderEnd(all)
            val headerText = String(all, 0, separator.first, Charsets.UTF_8)
            val status = headerText.lineSequence().first()
                .split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            return Response(status, all.copyOfRange(separator.second, all.size))
        }
    }

    private fun indexOfHeaderEnd(bytes: ByteArray): Pair<Int, Int> {
        for (i in 0 until bytes.size - 3) {
            if (bytes[i] == 13.toByte() && bytes[i + 1] == 10.toByte() &&
                bytes[i + 2] == 13.toByte() && bytes[i + 3] == 10.toByte()
            ) {
                return i to i + 4
            }
        }
        return bytes.size to bytes.size
    }
}
