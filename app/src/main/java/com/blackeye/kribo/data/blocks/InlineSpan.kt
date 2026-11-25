package com.blackeye.kribo.data.blocks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Inline rich-text style applied to a character range of a block's plain text. */
@Serializable
enum class SpanType {
    @SerialName("BOLD") BOLD,
    @SerialName("ITALIC") ITALIC,
    @SerialName("UNDERLINE") UNDERLINE,
    @SerialName("STRIKETHROUGH") STRIKETHROUGH,
    @SerialName("TEXT_COLOR") TEXT_COLOR,
    @SerialName("HIGHLIGHT") HIGHLIGHT,
}

/** The 5 palette tones, rendered adaptively on the 9 app themes (see InlineColors). */
@Serializable
enum class AccentTone {
    @SerialName("CYAN") CYAN,
    @SerialName("EMERALD") EMERALD,
    @SerialName("AMBER") AMBER,
    @SerialName("CORAL") CORAL,
    @SerialName("PURPLE") PURPLE,
}

/**
 * A styled character range ([start], [end)) inside a block's plain `text`.
 * Ranges are offsets into the plain string; empty ranges never exist.
 */
@Serializable
data class InlineSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val tone: AccentTone? = null,
)
