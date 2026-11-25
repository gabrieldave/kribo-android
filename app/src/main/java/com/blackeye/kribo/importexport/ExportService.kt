package com.blackeye.kribo.importexport

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import com.blackeye.kribo.data.AttachmentEntity
import com.blackeye.kribo.data.NoteEntity
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.titleText
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.InlineSpan
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.SpanType
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import androidx.compose.ui.graphics.toArgb
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Free export: clean Markdown, clean PDF and a full ZIP backup of standard .md files. */
/**
 * Plain text with markdown emphasis (#42): bold `**`, italic `*`, strikethrough `~~`,
 * underline `<u>`. Colors have no markdown and pass through untouched.
 */
internal fun renderMarkdownSpans(text: String, spans: List<InlineSpan>): String {
    if (spans.isEmpty() || text.isEmpty()) return text
    val ordered = spans.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))
    val out = StringBuilder()
    var cursor = 0
    ordered.forEach { span ->
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(start, text.length)
        if (start >= end) return@forEach
        if (start < cursor) return@forEach
        out.append(text, cursor, start)
        val chunk = text.substring(start, end)
        out.append(
            when (span.type) {
                SpanType.BOLD -> "**$chunk**"
                SpanType.ITALIC -> "*$chunk*"
                SpanType.UNDERLINE -> "<u>$chunk</u>"
                SpanType.STRIKETHROUGH -> "~~$chunk~~"
                SpanType.TEXT_COLOR, SpanType.HIGHLIGHT -> chunk
            },
        )
        cursor = end
    }
    out.append(text, cursor, text.length)
    return out.toString()
}

class ExportService(
    private val repository: NoteRepository,
) {

    fun renderMarkdown(
        note: NoteEntity,
        blocks: List<NoteBlock>,
        attachments: List<AttachmentEntity> = emptyList(),
    ): String {
        val builder = StringBuilder()
        val title = note.titleText.ifBlank { BlockCodec.derivedTitle(blocks) }
        if (title.isNotBlank()) builder.append("# ").append(title).append("\n\n")

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
                    builder.append(renderMarkdownSpans(block.text, block.spans)).append("\n\n")
                }

                is TaskBlock -> {
                    builder.append(if (block.checked) "- [x] " else "- [ ] ")
                        .append(renderMarkdownSpans(block.text, block.spans))
                        .append('\n')
                }

                is ToggleBlock -> {
                    builder.append("### ").append(block.title).append("\n\n")
                    if (block.content.isNotBlank()) builder.append(block.content).append("\n\n")
                }

                is QuoteBlock -> {
                    renderMarkdownSpans(block.text, block.spans).lineSequence().forEach { line ->
                        builder.append("> ").append(line).append('\n')
                    }
                    builder.append('\n')
                }

                is LinkBlock -> {
                    val url = block.url.trim()
                    if (url.isNotBlank()) {
                        val label = block.label.trim().ifBlank { url }
                        builder.append('[').append(label).append(']').append('(')
                            .append(url).append(")\n\n")
                    }
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
        val fontTypeface = when (com.blackeye.kribo.data.NoteFont.fromName(note.fontStyle)) {
            com.blackeye.kribo.data.NoteFont.SANS -> Typeface.DEFAULT
            com.blackeye.kribo.data.NoteFont.SERIF -> Typeface.SERIF
            com.blackeye.kribo.data.NoteFont.MONO -> Typeface.MONOSPACE
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 17, 17)
            textSize = 18f
            typeface = Typeface.create(fontTypeface, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 34, 34)
            textSize = 11f
            typeface = fontTypeface
        }
        val quotePaint = Paint(bodyPaint).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 10.5f
            typeface = Typeface.create(fontTypeface, Typeface.ITALIC)
        }
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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

        fun drawSpanned(text: String, spans: List<InlineSpan>, paint: Paint, indent: Float = 0f) {
            val spannable = android.text.SpannableString(text)
            spans.forEach { span ->
                val start = span.start.coerceIn(0, text.length)
                val end = span.end.coerceIn(start, text.length)
                if (start >= end) return@forEach
                when (span.type) {
                    SpanType.BOLD -> spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    SpanType.ITALIC -> spannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                        start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    SpanType.UNDERLINE -> spannable.setSpan(
                        android.text.style.UnderlineSpan(),
                        start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    SpanType.STRIKETHROUGH -> spannable.setSpan(
                        android.text.style.StrikethroughSpan(),
                        start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    SpanType.TEXT_COLOR -> span.tone?.let { tone ->
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(
                                com.blackeye.kribo.ui.theme.InlineColors.pdfColor(tone).toArgb(),
                            ),
                            start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    SpanType.HIGHLIGHT -> span.tone?.let { tone ->
                        spannable.setSpan(
                            android.text.style.BackgroundColorSpan(
                                com.blackeye.kribo.ui.theme.InlineColors.pdfColor(tone).toArgb().let {
                                    android.graphics.Color.argb(77, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
                                },
                            ),
                            start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
            }
            val width = (contentWidth - indent).toInt()
            val layout = android.text.StaticLayout.Builder.obtain(
                spannable, 0, text.length, android.text.TextPaint(paint), width,
            ).setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()
            ensureSpace(layout.height.toFloat() + 6f)
            canvas?.save()
            canvas?.translate(margin + indent, y)
            layout.draw(canvas)
            canvas?.restore()
            y += layout.height + 6f
        }

        fun drawParagraph(text: String, paint: Paint, indent: Float = 0f, spans: List<InlineSpan> = emptyList()) {
            if (spans.isNotEmpty() && text.isNotBlank()) {
                drawSpanned(text, spans, paint, indent)
                return
            }
            val width = contentWidth - indent
            wrapText(text, paint, width).forEach { line ->
                ensureSpace(paint.textSize + 4f)
                canvas?.drawText(line, margin + indent, y + paint.textSize, paint)
                y += paint.textSize + 4f
            }
            y += 6f
        }

        val pdfTitle = note.titleText.ifBlank { BlockCodec.derivedTitle(blocks) }
        startPage()
        if (pdfTitle.isNotBlank()) {
            canvas?.drawText(pdfTitle, margin, y + titlePaint.textSize, titlePaint)
            y += titlePaint.textSize + 12f
        }

        blocks.forEach { block ->
            when (block) {
                is TitleBlock -> if (block.text.trim() != note.title.trim()) {
                    drawParagraph(block.text, titlePaint, spans = block.spans)
                }

                is ParagraphBlock -> drawParagraph(block.text, bodyPaint, spans = block.spans)

                is TaskBlock -> {
                    // Prefix shifts the plain-text span coordinates for the drawn string.
                    val drawn = (if (block.checked) "[x] " else "[ ] ") + block.text
                    drawParagraph(drawn, bodyPaint, spans = block.spans.map {
                        it.copy(start = it.start + 4, end = it.end + 4)
                    })
                }

                is ToggleBlock -> {
                    drawParagraph(block.title, titlePaint, spans = block.titleSpans)
                    if (block.content.isNotBlank()) {
                        drawParagraph(block.content, bodyPaint, indent = 12f, spans = block.contentSpans)
                    }
                }

                is QuoteBlock -> drawParagraph(block.text, quotePaint, indent = 16f, spans = block.spans)

                is LinkBlock -> {
                    val label = block.label.trim().ifBlank { block.url.trim() }
                    if (label.isNotBlank()) drawParagraph(label, titlePaint, indent = 12f)
                    if (block.url.isNotBlank() && block.url.trim() != label) {
                        drawParagraph("↗ ${block.url.trim()}", bodyPaint, indent = 24f)
                    }
                }

                is AttachmentBlock -> {
                    val attachment = attachments.firstOrNull { it.id == block.attachmentId }
                    val file = attachment?.let { File(it.filePath) }
                    if (file != null && file.isFile && block.mimeType.startsWith("image/")) {
                        val bitmap = runCatching { decodeOrientedBitmap(file) }.getOrNull()
                        if (bitmap != null) {
                            // Preserve the real aspect ratio: scale to fit width and height,
                            // never stretch the pixels into a fixed box.
                            val maxWidth = contentWidth
                            val maxHeight = pageHeight * 0.55f
                            val scale = min(
                                maxWidth / bitmap.width.toFloat(),
                                maxHeight / bitmap.height.toFloat(),
                            )
                            val drawWidth = bitmap.width * scale
                            val drawHeight = bitmap.height * scale
                            ensureSpace(drawHeight + 10f)
                            val left = margin + (contentWidth - drawWidth) / 2f
                            canvas?.drawBitmap(
                                bitmap,
                                null,
                                android.graphics.RectF(left, y, left + drawWidth, y + drawHeight),
                                imagePaint,
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

    /**
     * Decodes a full-resolution bitmap and applies the EXIF orientation, so phone photos are
     * embedded upright and with their true aspect ratio instead of sideways/stretched.
     */
    private fun decodeOrientedBitmap(file: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) return bitmap
        val oriented = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
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
