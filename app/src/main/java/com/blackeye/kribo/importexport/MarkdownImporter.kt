package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Markdown → Kribo blocks. Supports the subset required by Notion and Obsidian exports:
 * headings, task lists, quotes, bullets and fenced code. ZIP entries become folders based on
 * their top-level directory, and referenced media (`![](assets/foto.png)`, `![[foto.png]]`) is
 * extracted and attached to the note instead of being discarded.
 */
class MarkdownImporter {

    fun parse(
        markdown: String,
        fallbackTitle: String = "",
        folderName: String? = null,
        imageResolver: ((String) -> ImportedAttachment?)? = null,
    ): ImportedNote {
        val content = stripFrontMatter(markdown.removePrefix("\uFEFF"))
        val blocks = mutableListOf<NoteBlock>()
        val attachments = LinkedHashMap<String, ImportedAttachment>()
        val paragraph = StringBuilder()
        val quote = StringBuilder()
        var explicitTitle = ""
        var inCodeFence = false

        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                blocks += ParagraphBlock(text = paragraph.toString().trim())
            }
            paragraph.clear()
        }

        fun flushQuote() {
            if (quote.isNotBlank()) {
                blocks += QuoteBlock(text = quote.toString().trim())
            }
            quote.clear()
        }

        fun addImage(reference: String, alt: String) {
            val resolved = imageResolver?.invoke(reference)
            if (resolved != null) {
                attachments[resolved.fileName] = resolved
                blocks += AttachmentBlock(
                    fileName = resolved.fileName,
                    mimeType = resolved.mimeType,
                )
            } else {
                blocks += ParagraphBlock(text = "Imagen: $alt")
            }
        }

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                flushQuote()
                flushParagraph()
                inCodeFence = !inCodeFence
                return@forEach
            }
            if (inCodeFence) {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
                return@forEach
            }
            if (trimmed.startsWith("---") || trimmed.startsWith("***")) {
                flushParagraph()
                flushQuote()
                return@forEach
            }

            val heading = HEADING.matchEntire(trimmed)
            if (heading != null) {
                flushParagraph()
                flushQuote()
                val level = heading.groupValues[1].length
                val text = heading.groupValues[2].trim()
                if (text.isNotEmpty()) {
                    if (level == 1 && explicitTitle.isBlank()) explicitTitle = text
                    blocks += TitleBlock(text = text, level = if (level <= 2) level else 2)
                }
                return@forEach
            }

            val task = TASK.matchEntire(trimmed)
            if (task != null) {
                flushParagraph()
                flushQuote()
                val checked = task.groupValues[1].equals("x", ignoreCase = true)
                val text = task.groupValues[2].trim()
                if (text.isNotEmpty()) blocks += TaskBlock(text = text, checked = checked)
                return@forEach
            }

            val quoted = QUOTE.matchEntire(trimmed)
            if (quoted != null) {
                flushParagraph()
                if (quote.isNotEmpty()) quote.append('\n')
                quote.append(quoted.groupValues[1].trim())
                return@forEach
            }

            val bullet = BULLET.matchEntire(trimmed)
            if (bullet != null) {
                flushParagraph()
                flushQuote()
                val text = bullet.groupValues[1].trim()
                if (text.isNotEmpty()) blocks += ParagraphBlock(text = "• $text")
                return@forEach
            }

            val numbered = NUMBERED.matchEntire(trimmed)
            if (numbered != null) {
                flushParagraph()
                flushQuote()
                val text = numbered.groupValues[1].trim()
                if (text.isNotEmpty()) blocks += ParagraphBlock(text = "• $text")
                return@forEach
            }

            val image = IMAGE.matchEntire(trimmed)
            if (image != null) {
                flushParagraph()
                flushQuote()
                val alt = image.groupValues[1].trim()
                val reference = image.groupValues[2].trim()
                addImage(reference, alt.ifBlank { reference.substringAfterLast('/') })
                return@forEach
            }

            val wikiImage = WIKI_IMAGE.matchEntire(trimmed)
            if (wikiImage != null) {
                flushParagraph()
                flushQuote()
                val reference = wikiImage.groupValues[1].substringBefore('|').trim()
                addImage(reference, reference.substringAfterLast('/'))
                return@forEach
            }

            if (trimmed.isEmpty()) {
                flushParagraph()
                flushQuote()
            } else {
                flushQuote()
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }

        flushParagraph()
        flushQuote()

        val title = explicitTitle.ifBlank { fallbackTitle }.ifBlank { BlockCodec.derivedTitle(blocks) }
        if (blocks.isEmpty() && title.isNotBlank()) {
            blocks += TitleBlock(text = title)
        }
        return ImportedNote(
            title = title,
            blocks = blocks,
            attachments = attachments.values.toList(),
            folderName = folderName,
        )
    }

    suspend fun parseZip(
        input: InputStream,
        onNote: suspend (ImportedNote) -> Unit,
    ): Int {
        // Read the archive once so markdown can resolve `assets/...` references regardless of
        // entry order. Obsidian/Notion exports are small enough for this to be safe.
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val media = entries.filterKeys(::isImage)
        var count = 0
        for ((name, bytes) in entries) {
            if (!name.endsWith(".md", ignoreCase = true)) continue
            val text = bytes.toString(Charsets.UTF_8)
            val baseName = name.substringAfterLast('/').removeSuffix(".md").removeSuffix(".MD")
            val baseDir = name.substringBeforeLast('/', "")
            val folder = name
                .split('/')
                .dropLast(1)
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
            val resolver: (String) -> ImportedAttachment? = { reference ->
                resolveMedia(reference, baseDir, entries, media)
            }
            onNote(parse(text, baseName, folder, resolver))
            count++
        }
        return count
    }

    private fun resolveMedia(
        reference: String,
        baseDir: String,
        entries: Map<String, ByteArray>,
        media: Map<String, ByteArray>,
    ): ImportedAttachment? {
        val cleaned = decode(reference)
            .substringBefore('#')
            .substringBefore('?')
            .trim()
            .removePrefix("./")
        if (cleaned.isBlank()) return null

        val candidates = buildList {
            add(cleaned)
            if (baseDir.isNotBlank()) add(resolvePath(baseDir, cleaned))
        }
        for (candidate in candidates) {
            entries[candidate]?.let { bytes -> return attachmentFor(candidate, bytes) }
            media[candidate]?.let { bytes -> return attachmentFor(candidate, bytes) }
        }

        val baseName = cleaned.substringAfterLast('/')
        val match = media.entries.firstOrNull { (path, _) ->
            path.substringAfterLast('/').equals(baseName, ignoreCase = true)
        } ?: return null
        return attachmentFor(match.key, match.value)
    }

    private fun attachmentFor(path: String, bytes: ByteArray): ImportedAttachment =
        ImportedAttachment(
            fileName = path.substringAfterLast('/').ifBlank { "imagen.jpg" },
            mimeType = mimeFor(path),
            bytes = bytes,
        )

    private fun resolvePath(baseDir: String, reference: String): String {
        val parts = ArrayDeque<String>()
        (baseDir.split('/') + reference.split('/')).forEach { part ->
            when {
                part.isBlank() || part == "." -> Unit
                part == ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun isImage(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

    private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    private fun stripFrontMatter(markdown: String): String {
        if (!markdown.startsWith("---")) return markdown
        val end = markdown.indexOf("\n---", startIndex = 3)
        if (end < 0) return markdown
        return markdown.substring(end + 4)
    }

    companion object {
        private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
        private val TASK = Regex("^[-*+]\\s+\\[([ xX])]\\s+(.+)$")
        private val QUOTE = Regex("^>\\s?(.*)$")
        private val BULLET = Regex("^[-*+]\\s+(.+)$")
        private val NUMBERED = Regex("^\\d+[.)]\\s+(.+)$")
        private val IMAGE = Regex("^!\\[([^]]*)]\\(([^)]+)\\)$")
        private val WIKI_IMAGE = Regex("^!\\[\\[([^]]+)]]$")
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
    }
}
