package com.blackeye.kribo.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.text.Normalizer
import java.util.Locale

/**
 * Accent- and case-insensitive search used by the list, the editor and OCR text.
 *
 * Text is folded with [Normalizer.Form.NFD], combining marks are dropped and the result is
 * lowercased. A folded index is mapped back to the original string so highlights always cover
 * the exact user-visible characters, even when `canción` is found from `cancion`.
 */
object SearchText {

    data class Match(val start: Int, val end: Int)

    private class Folded(
        val text: String,
        val starts: IntArray,
        val ends: IntArray,
    )

    fun fold(value: String): String = buildFolded(value).text

    fun contains(text: String?, query: String?): Boolean {
        if (text.isNullOrBlank() || query.isNullOrBlank()) return false
        return matches(text, query).isNotEmpty()
    }

    /** All non-overlapping matches of [query] inside [text], in original coordinates. */
    fun matches(text: String, query: String): List<Match> {
        val needle = buildFolded(query).text.trim()
        if (needle.isEmpty() || text.isEmpty()) return emptyList()
        val haystack = buildFolded(text)
        if (haystack.text.length < needle.length) return emptyList()

        val result = ArrayList<Match>()
        var from = 0
        while (from <= haystack.text.length - needle.length) {
            val index = haystack.text.indexOf(needle, from)
            if (index < 0) break
            val start = haystack.starts[index]
            val end = haystack.ends[index + needle.length - 1]
            result += Match(start, end)
            from = index + needle.length
        }
        return result
    }

    /**
     * Snippet centred on the first match. The matched word is always fully included: the window
     * expands instead of slicing right before it. Line breaks become spaces so cards stay tidy.
     */
    fun snippet(
        source: String,
        query: String,
        before: Int = 36,
        after: Int = 110,
        fallbackLength: Int = 180,
    ): String {
        if (source.isBlank()) return ""
        val flat = source
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
        if (query.isBlank()) return flat.take(fallbackLength)

        val match = matches(flat, query).firstOrNull() ?: return flat.take(fallbackLength)
        var start = (match.start - before).coerceAtLeast(0)
        var end = (match.end + after).coerceAtMost(flat.length)

        if (start > 0) {
            val space = flat.indexOf(' ', start)
            if (space in start until match.start) start = space + 1
        }
        if (end < flat.length) {
            val space = flat.lastIndexOf(' ', end)
            if (space in match.end until end) end = space
        }
        if (end <= start) {
            start = match.start
            end = match.end
        }
        return buildString {
            if (start > 0) append('…')
            append(flat.substring(start, end).trim())
            if (end < flat.length) append('…')
        }
    }

    /** [text] with every [query] occurrence carrying [style], ready for Compose `Text`. */
    fun highlight(text: String, query: String, style: SpanStyle): AnnotatedString {
        if (query.isBlank() || text.isEmpty()) return AnnotatedString(text)
        val ranges = matches(text, query)
        if (ranges.isEmpty()) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            ranges.forEach { match ->
                val start = match.start.coerceIn(0, text.length)
                val end = match.end.coerceIn(start, text.length)
                if (end > start) addStyle(style, start, end)
            }
        }
    }

    private fun buildFolded(value: String): Folded {
        val normalized = StringBuilder(value.length)
        val starts = ArrayList<Int>(value.length)
        val ends = ArrayList<Int>(value.length)
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val original = value.substring(index, index + charCount)
            val decomposed = Normalizer.normalize(original, Normalizer.Form.NFD)
                .lowercase(Locale.ROOT)
            var cursor = 0
            while (cursor < decomposed.length) {
                val current = decomposed.codePointAt(cursor)
                if (!isCombiningMark(current)) {
                    normalized.appendCodePoint(current)
                    starts += index
                    ends += index + charCount
                }
                cursor += Character.charCount(current)
            }
            index += charCount
        }
        return Folded(normalized.toString(), starts.toIntArray(), ends.toIntArray())
    }

    private fun isCombiningMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true

        else -> false
    }
}
