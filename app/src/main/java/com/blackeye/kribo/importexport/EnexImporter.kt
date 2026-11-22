package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import java.io.InputStream
import java.util.Base64
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Streaming Evernote `.enex` importer. Never loads the whole file into memory: each `<note>`
 * is emitted as soon as its closing tag is read.
 */
class EnexImporter {

    suspend fun parse(
        input: InputStream,
        onNote: suspend (ImportedNote) -> Unit,
    ): Int {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var count = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("note", ignoreCase = true)) {
                val note = readNote(parser)
                if (note != null) {
                    onNote(note)
                    count++
                }
            }
            event = parser.next()
        }
        return count
    }

    suspend fun parseAll(input: InputStream): List<ImportedNote> {
        val notes = mutableListOf<ImportedNote>()
        parse(input) { notes += it }
        return notes
    }

    private fun readNote(parser: XmlPullParser): ImportedNote? {
        var title = ""
        var content = ""
        val attachments = mutableListOf<ImportedAttachment>()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    parser.name.equals("title", ignoreCase = true) ->
                        title = parser.nextText().orEmpty()

                    parser.name.equals("content", ignoreCase = true) ->
                        content = parser.nextText().orEmpty()

                    parser.name.equals("resource", ignoreCase = true) ->
                        readResource(parser)?.let(attachments::add)
                }

                XmlPullParser.END_TAG -> if (parser.name.equals("note", ignoreCase = true)) {
                    val blocks = EnexHtmlConverter.convert(content).toMutableList()
                    val finalTitle = title.trim().ifBlank { BlockCodec.derivedTitle(blocks) }
                    if (blocks.isEmpty() && finalTitle.isNotEmpty()) {
                        blocks += com.blackeye.kribo.data.blocks.TitleBlock(text = finalTitle)
                    }
                    return ImportedNote(
                        title = finalTitle,
                        blocks = blocks,
                        attachments = attachments,
                    )
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun readResource(parser: XmlPullParser): ImportedAttachment? {
        var mime = "application/octet-stream"
        var fileName = ""
        var data = ""

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    parser.name.equals("data", ignoreCase = true) ->
                        data = parser.nextText().orEmpty()

                    parser.name.equals("mime", ignoreCase = true) ->
                        mime = parser.nextText().orEmpty().trim().ifBlank { mime }

                    parser.name.equals("file-name", ignoreCase = true) ->
                        fileName = parser.nextText().orEmpty().trim()
                }

                XmlPullParser.END_TAG -> if (parser.name.equals("resource", ignoreCase = true)) {
                    if (data.isBlank()) return null
                    val bytes = runCatching {
                        Base64.getMimeDecoder().decode(data.replace(Regex("\\s"), ""))
                    }.getOrNull() ?: return null
                    return ImportedAttachment(
                        fileName = fileName.ifBlank {
                            "enex-${System.currentTimeMillis()}.${extensionFor(mime)}"
                        },
                        mimeType = mime,
                        bytes = bytes,
                    )
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun extensionFor(mime: String): String = when {
        mime.contains("jpeg", ignoreCase = true) -> "jpg"
        mime.contains("png", ignoreCase = true) -> "png"
        mime.contains("pdf", ignoreCase = true) -> "pdf"
        mime.contains("gif", ignoreCase = true) -> "gif"
        else -> "bin"
    }
}
