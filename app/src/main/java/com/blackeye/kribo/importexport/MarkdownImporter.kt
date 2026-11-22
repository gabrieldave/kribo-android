package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Markdown → Kribo blocks. Supports the subset required by Notion and Obsidian exports:
 * headings, task lists, quotes, bullets and fenced code. ZIP entries become folders based on
 * their top-level directory.
 */
class MarkdownImporter {

    fun parse(
        markdown: String,
        fallbackTitle: String = "",
        folderName: String? = null,
    ): ImportedNote {
        val content = stripFrontMatter(markdown)
        val blocks = mutableListOf<NoteBlock>()
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

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                flushQuote()
                if (inCodeFence) flushParagraph() else flushParagraph()
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
                val alt = image.groupValues[1].ifBlank { image.groupValues[2] }
                blocks += ParagraphBlock(text = "Imagen: $alt")
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
            folderName = folderName,
        )
    }

    suspend fun parseZip(
        input: InputStream,
        onNote: suspend (ImportedNote) -> Unit,
    ): Int {
        var count = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".md", ignoreCase = true)) {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    val baseName = entry.name.substringAfterLast('/').removeSuffix(".md")
                    val folder = entry.name
                        .split('/')
                        .dropLast(1)
                        .firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                    onNote(parse(text, baseName, folder))
                    count++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return count
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
    }
}
