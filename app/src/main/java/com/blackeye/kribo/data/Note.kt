package com.blackeye.kribo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** First non-blank line, trimmed to a sane headline length. */
val Note.titleText: String
    get() = content
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.take(80)
        .orEmpty()

/** Remaining text after the headline, flattened for a compact preview. */
val Note.previewText: String
    get() = content
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .drop(1)
        .joinToString(separator = " ")
        .take(160)
