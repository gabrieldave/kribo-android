package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock

/**
 * Lightweight Evernote XHTML → Kribo blocks conversion.
 * Avoids android.text.Html so it is fully unit-testable.
 */
object EnexHtmlConverter {

    private val todoChecked = Regex("(?i)<en-todo[^>]*checked\\s*=\\s*\"true\"[^>]*/?>")
    private val todoOpen = Regex("(?i)<en-todo[^>]*/?>")
    private val breaks = Regex("(?i)<br\\s*/?>")
    private val blockEnd = Regex("(?i)</(div|p|li|h1|h2|h3|h4|blockquote)>")
    private val headingStart = Regex("(?i)<h([1-4])[^>]*>")
    private val quoteStart = Regex("(?i)<blockquote[^>]*>")
    private val tags = Regex("<[^>]+>")
    private val entity = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    fun convert(html: String): List<NoteBlock> {
        var text = html
        text = todoChecked.replace(text, "[x] ")
        text = todoOpen.replace(text, "[ ] ")
        text = breaks.replace(text, "\n")
        text = quoteStart.replace(text, "\n> ")
        text = blockEnd.replace(text, "\n")
        text = headingStart.replace(text) { match ->
            val level = match.groupValues[1].toIntOrNull() ?: 1
            "\n" + if (level <= 2) "# " else "### "
        }
        text = tags.replace(text, "")
        text = decodeEntities(text)

        val blocks = ArrayList<NoteBlock>()
        text.lineSequence()
            .map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                when {
                    line.startsWith("#") -> {
                        val level = line.takeWhile { it == '#' }.length.coerceIn(1, 2)
                        val value = line.dropWhile { it == '#' }.trim()
                        if (value.isNotEmpty()) blocks += TitleBlock(text = value, level = level)
                    }

                    line.startsWith(">") -> {
                        val value = line.removePrefix(">").trim()
                        if (value.isNotEmpty()) blocks += QuoteBlock(text = value)
                    }

                    line.startsWith("[x] ", ignoreCase = true) ->
                        blocks += TaskBlock(text = line.substring(4).trim(), checked = true)

                    line.startsWith("[ ] ") ->
                        blocks += TaskBlock(text = line.substring(4).trim(), checked = false)

                    else -> blocks += ParagraphBlock(text = line)
                }
            }
        return blocks
    }

    fun decodeEntities(value: String): String = entity.replace(value) { match ->
        val token = match.groupValues[1]
        when {
            token.equals("amp", ignoreCase = true) -> "&"
            token.equals("lt", ignoreCase = true) -> "<"
            token.equals("gt", ignoreCase = true) -> ">"
            token.equals("quot", ignoreCase = true) -> "\""
            token.equals("apos", ignoreCase = true) -> "'"
            token.equals("nbsp", ignoreCase = true) -> " "
            token.startsWith("#x", ignoreCase = true) ->
                token.drop(2).toIntOrNull(16)?.toChar()?.toString() ?: match.value

            token.startsWith("#") ->
                token.drop(1).toIntOrNull()?.toChar()?.toString() ?: match.value

            else -> match.value
        }
    }

    fun plainTextTitle(blocks: List<NoteBlock>): String = BlockCodec.derivedTitle(blocks)
}
