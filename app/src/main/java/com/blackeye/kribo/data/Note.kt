package com.blackeye.kribo.data

/** First non-blank line fallback for notes whose stored title is empty. */
val NoteEntity.titleText: String
    get() = title.ifBlank {
        plainTextIndex.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

/** Short body preview without the headline. */
val NoteEntity.previewText: String
    get() = plainTextIndex
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .drop(1)
        .joinToString(separator = " ")
        .take(180)
