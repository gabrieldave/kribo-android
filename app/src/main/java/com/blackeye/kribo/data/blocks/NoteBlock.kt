package com.blackeye.kribo.data.blocks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Lightweight block model for the human editor.
 *
 * Stored as JSON in `NoteEntity.blocksJson`. Keep every new block @Serializable and
 * additive: unknown fields are ignored so older installs never crash on newer notes.
 */
@Serializable
sealed interface NoteBlock {
    val id: String

    fun plainText(): String
}

@Serializable
@SerialName("title")
data class TitleBlock(
    override val id: String = BlockCodec.newId(),
    val text: String = "",
    val level: Int = 1,
    val spans: List<InlineSpan> = emptyList(),
) : NoteBlock {
    override fun plainText(): String = text
}

@Serializable
@SerialName("paragraph")
data class ParagraphBlock(
    override val id: String = BlockCodec.newId(),
    val text: String = "",
    val spans: List<InlineSpan> = emptyList(),
) : NoteBlock {
    override fun plainText(): String = text
}

@Serializable
@SerialName("task")
data class TaskBlock(
    override val id: String = BlockCodec.newId(),
    val text: String = "",
    val checked: Boolean = false,
    val spans: List<InlineSpan> = emptyList(),
) : NoteBlock {
    override fun plainText(): String = if (checked) "[x] $text" else "[ ] $text"
}

@Serializable
@SerialName("toggle")
data class ToggleBlock(
    override val id: String = BlockCodec.newId(),
    val title: String = "",
    val content: String = "",
    val expanded: Boolean = false,
    val titleSpans: List<InlineSpan> = emptyList(),
    val contentSpans: List<InlineSpan> = emptyList(),
) : NoteBlock {
    override fun plainText(): String = listOf(title, content)
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")

    companion object {
        /**
         * A section inserted by the user starts **open** so the title and the inner field are
         * visible immediately. Saved notes keep whatever `expanded` value they were stored with
         * (the data-class default stays `false`, which is what older payloads decode to).
         */
        fun newSection(title: String = "", content: String = ""): ToggleBlock =
            ToggleBlock(title = title, content = content, expanded = true)
    }
}

@Serializable
@SerialName("quote")
data class QuoteBlock(
    override val id: String = BlockCodec.newId(),
    val text: String = "",
    val spans: List<InlineSpan> = emptyList(),
) : NoteBlock {
    override fun plainText(): String = text
}

@Serializable
@SerialName("link")
data class LinkBlock(
    override val id: String = BlockCodec.newId(),
    /** Raw text as typed; opened through [normalizeUrl], which only allows http/https. */
    val url: String = "",
    /** Optional display label; backs use the URL when blank. Older JSON without it decodes empty. */
    val label: String = "",
) : NoteBlock {
    override fun plainText(): String = listOf(label, url)
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
}

@Serializable
@SerialName("attachment")
data class AttachmentBlock(
    override val id: String = BlockCodec.newId(),
    val attachmentId: Long? = null,
    val fileName: String = "",
    val mimeType: String = "image/jpeg",
    val previewText: String = "",
    /**
     * 1-based page number for scanned documents. `null`/0 means a regular image attached by
     * the user. It is stored in JSON only (no Room change) and defaults on older notes.
     */
    val pageNumber: Int = 0,
    /** True only for pictures attached manually from camera/gallery (PRO quota). */
    val manual: Boolean = false,
) : NoteBlock {
    override fun plainText(): String = previewText
}

object BlockCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        prettyPrint = false
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun encode(blocks: List<NoteBlock>): String =
        if (blocks.isEmpty()) "[]" else json.encodeToString(blocks)

    fun decode(raw: String?): List<NoteBlock> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<NoteBlock>>(raw)
        }.getOrElse { emptyList() }
    }

    fun plainText(blocks: List<NoteBlock>): String = blocks
        .map { it.plainText().trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = "\n")

    fun derivedTitle(blocks: List<NoteBlock>): String {
        val explicit = blocks.filterIsInstance<TitleBlock>()
            .firstOrNull { it.text.isNotBlank() }
            ?.text
        val fallback = plainText(blocks).lineSequence().firstOrNull { it.isNotBlank() }
        return (explicit ?: fallback.orEmpty()).trim().take(TITLE_MAX_LENGTH)
    }

    fun legacyParagraphs(content: String): List<NoteBlock> =
        if (content.isBlank()) emptyList() else listOf(ParagraphBlock(text = content))

    private const val TITLE_MAX_LENGTH = 120
}
