package com.blackeye.kribo.importexport

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.blackeye.kribo.data.AttachmentEntity
import com.blackeye.kribo.data.NoteEntity
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Free export: clean Markdown, clean PDF and a full ZIP backup of standard .md files. */
class ExportService(
    private val repository: NoteRepository,
) {

    fun renderMarkdown(
        note: NoteEntity,
        blocks: List<NoteBlock>,
        attachments: List<AttachmentEntity> = emptyList(),
    ): String {
        val builder = StringBuilder()
        val title = note.title.ifBlank { "Nota sin título" }
        builder.append("# ").append(title).append("\n\n")

        blocks.forEach { block ->
            when (block) {
                is TitleBlock -> {
                    if (block.text.isNotBlank() && block.text.trim() != title) {
                        builder.append("#".repeat(block.level.coerceIn(1, 6)))
                            .append(' ')
                            .append(block.text)
                            .append("\n\n")
                    }
                }

                is ParagraphBlock -> if (block.text.isNotBlank()) {
                    builder.append(block.text).append("\n\n")
                }

                is TaskBlock -> {
                    builder.append(if (block.checked) "- [x] " else "- [ ] ")
                        .append(block.text)
                        .append('\n')
                }

                is ToggleBlock -> {
                    builder.append("### ").append(block.title).append("\n\n")
                    if (block.content.isNotBlank()) builder.append(block.content).append("\n\n")
                }

                is QuoteBlock -> {
                    block.text.lineSequence().forEach { line ->
                        builder.append("> ").append(line).append('\n')
                    }
                    builder.append('\n')
                }

                is AttachmentBlock -> {
                    builder.append("![").append(block.fileName).append("](")
                        .append("attachments/").append(note.id).append('/')
                        .append(block.fileName).append(")\n\n")
                    val extracted = attachments
                        .firstOrNull { it.id == block.attachmentId }
                        ?.extractedText
                        .orEmpty()
                    if (extracted.isNotBlank()) {
                        builder.append("> Texto OCR:\n")
                        extracted.lineSequence().forEach { line ->
                            builder.append("> ").append(line).append('\n')
                        }
                        builder.append('\n')
                    }
                }
            }
            if (block is TaskBlock) builder.append('\n')
        }
        return builder.toString().trimEnd() + "\n"
    }

    suspend fun exportNoteMarkdown(noteId: Long, outputFile: File): File = withContext(Dispatchers.IO) {
        val note = repository.getNote(noteId) ?: error("Note not found")
        val blocks = BlockCodec.decode(note.blocksJson)
        val attachments = repository.getAttachments(noteId)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(renderMarkdown(note, blocks, attachments))
        outputFile
    }

    suspend fun exportNotePdf(noteId: Long, outputFile: File): File = withContext(Dispatchers.IO) {
        val note = repository.getNote(noteId) ?: error("Note not found")
        val blocks = BlockCodec.decode(note.blocksJson)
        val attachments = repository.getAttachments(noteId)
        outputFile.parentFile?.mkdirs()

        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 17, 17)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 34, 34)
            textSize = 11f
        }
        val quotePaint = Paint(bodyPaint).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val metaPaint = Paint(bodyPaint).apply {
            color = Color.rgb(120, 120, 120)
            textSize = 8.5f
        }

        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val contentWidth = pageWidth - margin * 2
        var page: PdfDocument.Page? = null
        var canvas = null as android.graphics.Canvas?
        var y = margin

        fun startPage() {
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create())
            canvas = page!!.canvas
            y = margin
        }

        fun finishPage() {
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        fun ensureSpace(height: Float) {
            if (canvas == null) startPage()
            if (y + height > pageHeight - margin) {
                finishPage()
                startPage()
            }
        }

        fun drawParagraph(text: String, paint: Paint, indent: Float = 0f) {
            val width = contentWidth - indent
            wrapText(text, paint, width).forEach { line ->
                ensureSpace(paint.textSize + 4f)
                canvas?.drawText(line, margin + indent, y + paint.textSize, paint)
                y += paint.textSize + 4f
            }
            y += 6f
        }

        startPage()
        canvas?.drawText(note.title.ifBlank { "Nota sin título" }, margin, y + titlePaint.textSize, titlePaint)
        y += titlePaint.textSize + 12f

        blocks.forEach { block ->
            when (block) {
                is TitleBlock -> if (block.text.trim() != note.title.trim()) {
                    drawParagraph(block.text, titlePaint)
                }

                is ParagraphBlock -> drawParagraph(block.text, bodyPaint)

                is TaskBlock -> drawParagraph(
                    (if (block.checked) "[x] " else "[ ] ") + block.text,
                    bodyPaint,
                )

                is ToggleBlock -> {
                    drawParagraph(block.title, titlePaint)
                    if (block.content.isNotBlank()) drawParagraph(block.content, bodyPaint, indent = 12f)
                }

                is QuoteBlock -> drawParagraph(block.text, quotePaint, indent = 16f)

                is AttachmentBlock -> {
                    val attachment = attachments.firstOrNull { it.id == block.attachmentId }
                    drawParagraph("Adjunto: ${block.fileName}", metaPaint)
                    val file = attachment?.let { File(it.filePath) }
                    if (file != null && file.isFile && block.mimeType.startsWith("image/")) {
                        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                        if (bitmap != null) {
                            val ratio = contentWidth / bitmap.width.toFloat()
                            val drawHeight = (bitmap.height * ratio).coerceAtMost(pageHeight * 0.45f)
                            ensureSpace(drawHeight + 10f)
                            canvas?.drawBitmap(
                                bitmap,
                                null,
                                android.graphics.RectF(margin, y, margin + contentWidth, y + drawHeight),
                                null,
                            )
                            y += drawHeight + 10f
                            bitmap.recycle()
                        }
                    }
                    val ocr = attachment?.extractedText.orEmpty()
                    if (ocr.isNotBlank()) drawParagraph(ocr, quotePaint, indent = 12f)
                }
            }
        }
        finishPage()

        outputFile.outputStream().use { document.writeTo(it) }
        document.close()
        outputFile
    }

    /** Writes every note as a standard Markdown file plus its attachments. */
    suspend fun writeBackup(
        output: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val notes = repository.getAllNotes()
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            notes.forEachIndexed { index, note ->
                val blocks = BlockCodec.decode(note.blocksJson)
                val attachments = repository.getAttachments(note.id)
                val markdown = renderMarkdown(note, blocks, attachments)
                val safeTitle = note.title
                    .ifBlank { "nota" }
                    .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
                    .trim('-')
                    .take(60)
                    .ifBlank { "nota" }

                zip.putNextEntry(ZipEntry("notas/${safeTitle}-${note.id}.md"))
                zip.write(markdown.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                attachments.forEach { attachment ->
                    val file = File(attachment.filePath)
                    if (file.isFile) {
                        zip.putNextEntry(ZipEntry("attachments/${note.id}/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                onProgress(index + 1, notes.size)
            }
        }
        notes.size
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) return@forEach
            var current = StringBuilder()
            paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines += current.toString()
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
        }
        return lines.ifEmpty { listOf("") }
    }
}
