package com.blackeye.kribo.data.blocks

/** The styles currently active at the caret (empty selection) or over a selection. */
data class ActiveStyles(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val textTone: AccentTone? = null,
    val highlightTone: AccentTone? = null,
) {
    val isEmpty: Boolean
        get() = !bold && !italic && !underline && !strikethrough &&
            textTone == null && highlightTone == null
}

/**
 * Pure inline-formatting engine (#42). Ranges are offsets into a block's plain text;
 * every function returns normalized spans (clipped, sorted, adjacent-equal merged).
 */
object SpanEditor {

    /**
     * Toggles [type] on [start, end): fully covered → removed; partially covered → completed.
     * TEXT_COLOR/HIGHLIGHT replace the previous tone in the range; `tone == null` clears it.
     */
    fun toggleStyle(
        spans: List<InlineSpan>,
        start: Int,
        end: Int,
        type: SpanType,
        tone: AccentTone? = null,
        textLength: Int = Int.MAX_VALUE,
    ): List<InlineSpan> {
        val s = start.coerceAtLeast(0)
        val e = end.coerceAtMost(textLength).coerceAtLeast(s)
        if (s >= e) return spans
        val isColor = type == SpanType.TEXT_COLOR || type == SpanType.HIGHLIGHT
        return if (isColor) {
            // New tone replaces the previous one inside the range; null clears it.
            val base = subtract(spans, type, s, e)
            if (tone == null) normalize(base) else normalize(base + InlineSpan(s, e, type, tone))
        } else if (isFullyCovered(spans, type, s, e)) {
            normalize(subtract(spans, type, s, e))
        } else {
            normalize(spans + InlineSpan(s, e, type, null))
        }
    }

    /** Removes every style overlapping [start, end); empty selection clears the whole block. */
    fun clearFormatting(spans: List<InlineSpan>, start: Int, end: Int): List<InlineSpan> {
        if (start >= end) return emptyList()
        val s = start.coerceAtLeast(0)
        val e = end.coerceAtLeast(s)
        return normalize(
            spans
                .mapNotNull { span ->
                    val pieces = listOf(span.start to minOf(span.end, s), maxOf(span.start, e) to span.end)
                        .filter { (a, b) -> a < b }
                    if (pieces.isEmpty()) null
                    else pieces.map { (a, b) -> InlineSpan(a, b, span.type, span.tone) }
                }
                .flatMap { it },
        )
    }

    /**
     * Maps spans across a text edit: the common prefix/suffix of [oldText]/[newText] anchors
     * the unchanged parts, and [active] styles are applied to the inserted region.
     */
    fun adjustSpansOnEdit(
        oldText: String,
        newText: String,
        spans: List<InlineSpan>,
        active: ActiveStyles,
    ): List<InlineSpan> {
        if (newText == oldText) return spans
        var prefix = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < minOf(oldText.length - prefix, newText.length - prefix) &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }
        val oldEnd = oldText.length - suffix
        val newEnd = newText.length - suffix
        val delta = newEnd - oldEnd
        val moved = spans
            .mapNotNull { span ->
                val start = span.start
                val end = span.end
                // Before the edit: unchanged. After it: shifted. Inside it: cut away.
                val a = when {
                    start <= prefix -> start
                    start >= oldEnd -> start + delta
                    else -> prefix
                }
                val b = when {
                    end <= prefix -> end
                    end >= oldEnd -> end + delta
                    else -> prefix
                }
                if (a < b) InlineSpan(a, b, span.type, span.tone) else null
            }
        val result = if (prefix < newEnd && !active.isEmpty) {
            var out = moved
            if (active.bold) out += InlineSpan(prefix, newEnd, SpanType.BOLD)
            if (active.italic) out += InlineSpan(prefix, newEnd, SpanType.ITALIC)
            if (active.underline) out += InlineSpan(prefix, newEnd, SpanType.UNDERLINE)
            if (active.strikethrough) out += InlineSpan(prefix, newEnd, SpanType.STRIKETHROUGH)
            active.textTone?.let { out += InlineSpan(prefix, newEnd, SpanType.TEXT_COLOR, it) }
            active.highlightTone?.let { out += InlineSpan(prefix, newEnd, SpanType.HIGHLIGHT, it) }
            out
        } else {
            moved
        }
        return normalize(result)
    }

    /** Splits spans at [offset]: left keeps [0, offset), right is re-based at 0. */
    fun splitSpans(spans: List<InlineSpan>, offset: Int): Pair<List<InlineSpan>, List<InlineSpan>> {
        val cut = offset.coerceAtLeast(0)
        val left = spans
            .mapNotNull { span ->
                val end = minOf(span.end, cut)
                if (span.start < end) InlineSpan(span.start, end, span.type, span.tone) else null
            }
        val right = spans
            .mapNotNull { span ->
                val start = maxOf(span.start, cut)
                if (start < span.end) InlineSpan(start - cut, span.end - cut, span.type, span.tone) else null
            }
        return normalize(left) to normalize(right)
    }

    /** Which styles cover the caret ([start] when collapsed) or the whole selection. */
    fun activeStylesAt(spans: List<InlineSpan>, start: Int, end: Int): ActiveStyles {
        val probe = if (start == end) maxOf(start - 1, 0) else start
        fun covers(type: SpanType, at: Int): Boolean =
            spans.any { it.type == type && it.start <= at && at < it.end }
        fun toneOf(type: SpanType): AccentTone? =
            spans.firstOrNull { it.type == type && it.start <= probe && probe < it.end }?.tone
        return ActiveStyles(
            bold = covers(SpanType.BOLD, probe),
            italic = covers(SpanType.ITALIC, probe),
            underline = covers(SpanType.UNDERLINE, probe),
            strikethrough = covers(SpanType.STRIKETHROUGH, probe),
            textTone = toneOf(SpanType.TEXT_COLOR),
            highlightTone = toneOf(SpanType.HIGHLIGHT),
        )
    }

    /** True when every character of [s, e) is already covered by a span of [type]. */
    private fun isFullyCovered(spans: List<InlineSpan>, type: SpanType, s: Int, e: Int): Boolean {
        var cursor = s
        val intervals = spans
            .filter { it.type == type }
            .map { it.start to minOf(it.end, e) }
            .filter { (a, b) -> a < b }
            .sortedBy { it.first }
        for ((a, b) in intervals) {
            if (a > cursor) return false
            cursor = maxOf(cursor, b)
            if (cursor >= e) return true
        }
        return cursor >= e
    }

    /** Removes [s, e) from the spans of [type], keeping up to two pieces per span. */
    private fun subtract(spans: List<InlineSpan>, type: SpanType, s: Int, e: Int): List<InlineSpan> =
        spans.flatMap { span ->
            if (span.type != type) {
                listOf(span)
            } else {
                listOf(span.start to minOf(span.end, s), maxOf(span.start, e) to span.end)
                    .filter { (a, b) -> a < b }
                    .map { (a, b) -> InlineSpan(a, b, span.type, span.tone) }
            }
        }

    /** Normal form: clipped, non-empty, sorted; adjacent/overlapping equal ranges merged. */
    fun normalize(spans: List<InlineSpan>): List<InlineSpan> {
        val clipped = spans.filter { it.start < it.end }
        val merged = clipped
            .groupBy { it.type to it.tone }
            .flatMap { (_, group) ->
                val sorted = group.sortedWith(compareBy({ it.start }, { it.end }))
                val out = mutableListOf<InlineSpan>()
                for (span in sorted) {
                    val last = out.lastOrNull()
                    if (last != null && last.type == span.type && last.tone == span.tone &&
                        last.end >= span.start
                    ) {
                        out[out.lastIndex] = last.copy(end = maxOf(last.end, span.end))
                    } else {
                        out += span
                    }
                }
                out
            }
        return merged.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }, { it.type.ordinal }))
    }
}
